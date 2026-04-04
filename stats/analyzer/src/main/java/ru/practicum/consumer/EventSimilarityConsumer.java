package ru.practicum.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import ru.practicum.ewm.stats.avro.EventSimilarityAvro;
import ru.practicum.model.EventSimilarity;
import ru.practicum.model.EventSimilarityId;
import ru.practicum.repository.EventSimilarityRepository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Component
@Slf4j
@RequiredArgsConstructor
public class EventSimilarityConsumer {

    private final EventSimilarityRepository repository;

    @KafkaListener(topics = "${analyzer.kafka.topic.event-similarity:stats.events-similarity.v1}",
            containerFactory = "similarityListenerFactory")
    public void consume(EventSimilarityAvro similarity) {
        log.debug("Received event similarity: eventA={}, eventB={}, score={}",
                similarity.getEventA(), similarity.getEventB(), similarity.getScore());

        EventSimilarityId id = new EventSimilarityId(similarity.getEventA(), similarity.getEventB());
        LocalDateTime ts = LocalDateTime.ofInstant(similarity.getTimestamp(), ZoneOffset.UTC);

        repository.save(EventSimilarity.builder()
                .id(id)
                .score(similarity.getScore())
                .timestamp(ts)
                .build());

        log.debug("Saved event similarity: eventA={}, eventB={}, score={}",
                id.getEventA(), id.getEventB(), similarity.getScore());
    }
}
