package ru.practicum.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import ru.practicum.ewm.stats.avro.ActionTypeAvro;
import ru.practicum.ewm.stats.avro.EventSimilarityAvro;
import ru.practicum.ewm.stats.avro.UserActionAvro;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class AggregatorService {

    private final KafkaTemplate<String, EventSimilarityAvro> kafkaTemplate;

    private static final String SIMILARITY_TOPIC = "stats.events-similarity.v1";

    // event → (user → maxWeight)
    private final Map<Long, Map<Long, Double>> eventUserWeights = new HashMap<>();

    // event → sum of all user weights
    private final Map<Long, Double> eventWeightSums = new HashMap<>();

    // min(eventA, eventB) → max(eventA, eventB) → Smin
    private final Map<Long, Map<Long, Double>> minWeightsSums = new HashMap<>();

    @KafkaListener(topics = "stats.user-actions.v1")
    public void processUserAction(UserActionAvro action) {
        long userId = action.getUserId();
        long eventId = action.getEventId();
        double newWeight = getWeight(action.getActionType());

        log.info("Processing action: userId={}, eventId={}, type={}, weight={}",
                userId, eventId, action.getActionType(), newWeight);

        // Get current max weight for this user+event
        Map<Long, Double> userWeights = eventUserWeights.computeIfAbsent(eventId, k -> new HashMap<>());
        double oldWeight = userWeights.getOrDefault(userId, 0.0);

        // Only process if new weight is greater than old
        if (newWeight <= oldWeight) {
            log.debug("Weight not increased for userId={}, eventId={}, skipping", userId, eventId);
            return;
        }

        // Update max weight
        userWeights.put(userId, newWeight);

        // Update weight sum for this event
        double weightDelta = newWeight - oldWeight;
        eventWeightSums.merge(eventId, weightDelta, Double::sum);

        // Recalculate similarity with all other events
        java.time.Instant timestamp = action.getTimestamp();

        for (Map.Entry<Long, Map<Long, Double>> otherEntry : eventUserWeights.entrySet()) {
            long otherEventId = otherEntry.getKey();
            if (otherEventId == eventId) continue;

            Map<Long, Double> otherUserWeights = otherEntry.getValue();

            // Check if this user interacted with the other event
            Double otherWeight = otherUserWeights.get(userId);
            if (otherWeight == null) continue;

            // Calculate delta for Smin
            double oldMin = Math.min(oldWeight, otherWeight);
            double newMin = Math.min(newWeight, otherWeight);
            double minDelta = newMin - oldMin;

            // Update Smin for this pair
            if (minDelta != 0.0) {
                long first = Math.min(eventId, otherEventId);
                long second = Math.max(eventId, otherEventId);
                double currentSmin = getMinWeightSum(first, second);
                putMinWeightSum(first, second, currentSmin + minDelta);
            }

            // Calculate similarity
            long first = Math.min(eventId, otherEventId);
            long second = Math.max(eventId, otherEventId);

            double sMin = getMinWeightSum(first, second);
            double sA = eventWeightSums.getOrDefault(first, 0.0);
            double sB = eventWeightSums.getOrDefault(second, 0.0);

            double similarity = 0.0;
            if (sA > 0 && sB > 0) {
                similarity = sMin / (Math.sqrt(sA) * Math.sqrt(sB));
            }

            // Send to Kafka
            EventSimilarityAvro similarityAvro = EventSimilarityAvro.newBuilder()
                    .setEventA(first)
                    .setEventB(second)
                    .setScore(similarity)
                    .setTimestamp(timestamp)
                    .build();

            kafkaTemplate.send(SIMILARITY_TOPIC, first + "-" + second, similarityAvro);

            log.debug("Similarity({}, {}) = {}", first, second, similarity);
        }
    }

    private double getWeight(ActionTypeAvro actionType) {
        return switch (actionType) {
            case VIEW -> 0.4;
            case REGISTER -> 0.8;
            case LIKE -> 1.0;
        };
    }

    private double getMinWeightSum(long eventA, long eventB) {
        long first = Math.min(eventA, eventB);
        long second = Math.max(eventA, eventB);
        return minWeightsSums
                .computeIfAbsent(first, k -> new HashMap<>())
                .getOrDefault(second, 0.0);
    }

    private void putMinWeightSum(long eventA, long eventB, double sum) {
        long first = Math.min(eventA, eventB);
        long second = Math.max(eventA, eventB);
        minWeightsSums
                .computeIfAbsent(first, k -> new HashMap<>())
                .put(second, sum);
    }
}
