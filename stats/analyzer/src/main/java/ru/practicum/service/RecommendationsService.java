package ru.practicum.service;

import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import ru.practicum.model.EventSimilarity;
import ru.practicum.model.UserAction;
import ru.practicum.repository.EventSimilarityRepository;
import ru.practicum.repository.UserActionRepository;
import stats.service.dashboard.InteractionsCountRequestProto;
import stats.service.dashboard.RecommendedEventProto;
import stats.service.dashboard.RecommendationsControllerGrpc;
import stats.service.dashboard.SimilarEventsRequestProto;
import stats.service.dashboard.UserPredictionsRequestProto;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@GrpcService
@Slf4j
@RequiredArgsConstructor
public class RecommendationsService extends RecommendationsControllerGrpc.RecommendationsControllerImplBase {

    private static final int NEIGHBOR_COUNT = 5;

    private final EventSimilarityRepository similarityRepository;
    private final UserActionRepository actionRepository;

    @Override
    public void getRecommendationsForUser(UserPredictionsRequestProto request,
                                          StreamObserver<RecommendedEventProto> responseObserver) {
        long userId = request.getUserId();
        int maxResults = request.getMaxResults();

        log.debug("Getting recommendations for userId={}, maxResults={}", userId, maxResults);

        List<UserAction> userActions = actionRepository.findByUserIdOrderByTimestampDesc(userId);
        if (userActions.isEmpty()) {
            responseObserver.onCompleted();
            return;
        }

        Set<Long> interactedEventIds = userActions.stream()
                .map(a -> a.getId().getEventId())
                .collect(Collectors.toSet());

        List<Long> recentEventIds = userActions.stream()
                .limit(Math.min(maxResults * 2L, 20))
                .map(a -> a.getId().getEventId())
                .toList();

        Map<Long, Double> candidateScores = new HashMap<>();
        for (Long eventId : recentEventIds) {
            List<EventSimilarity> similarities = similarityRepository.findByEventId(eventId);
            for (EventSimilarity sim : similarities) {
                long otherEventId = sim.getId().getEventA().equals(eventId)
                        ? sim.getId().getEventB() : sim.getId().getEventA();
                if (!interactedEventIds.contains(otherEventId)) {
                    candidateScores.merge(otherEventId, sim.getScore(), Math::max);
                }
            }
        }

        List<Long> topCandidates = candidateScores.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(maxResults)
                .map(Map.Entry::getKey)
                .toList();

        for (Long candidateId : topCandidates) {
            List<EventSimilarity> sims = similarityRepository.findByEventId(candidateId);

            List<Map.Entry<Long, Double>> neighbors = sims.stream()
                    .map(sim -> {
                        long otherEventId = sim.getId().getEventA().equals(candidateId)
                                ? sim.getId().getEventB() : sim.getId().getEventA();
                        return Map.entry(otherEventId, sim.getScore());
                    })
                    .filter(e -> interactedEventIds.contains(e.getKey()))
                    .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                    .limit(NEIGHBOR_COUNT)
                    .toList();

            if (neighbors.isEmpty()) {
                continue;
            }

            List<Long> neighborIds = neighbors.stream().map(Map.Entry::getKey).toList();
            List<UserAction> neighborActions = actionRepository.findByUserIdAndEventIds(userId, neighborIds);
            Map<Long, Double> ratings = neighborActions.stream()
                    .collect(Collectors.toMap(a -> a.getId().getEventId(), UserAction::getMaxWeight));

            double weightedSum = 0;
            double simSum = 0;
            for (Map.Entry<Long, Double> neighbor : neighbors) {
                Double rating = ratings.get(neighbor.getKey());
                if (rating != null) {
                    weightedSum += neighbor.getValue() * rating;
                    simSum += neighbor.getValue();
                }
            }

            double predictedRating = simSum > 0 ? weightedSum / simSum : 0;

            responseObserver.onNext(RecommendedEventProto.newBuilder()
                    .setEventId(candidateId)
                    .setScore(predictedRating)
                    .build());
        }

        responseObserver.onCompleted();
    }

    @Override
    public void getSimilarEvents(SimilarEventsRequestProto request,
                                 StreamObserver<RecommendedEventProto> responseObserver) {
        long eventId = request.getEventId();
        long userId = request.getUserId();
        int maxResults = request.getMaxResults();

        log.debug("Getting similar events for eventId={}, userId={}, maxResults={}", eventId, userId, maxResults);

        List<EventSimilarity> similarities = similarityRepository.findByEventId(eventId);

        Set<Long> interactedEventIds = actionRepository.findByUserIdOrderByTimestampDesc(userId).stream()
                .map(a -> a.getId().getEventId())
                .collect(Collectors.toSet());

        similarities.stream()
                .map(sim -> {
                    long otherEventId = sim.getId().getEventA().equals(eventId)
                            ? sim.getId().getEventB() : sim.getId().getEventA();
                    return Map.entry(otherEventId, sim.getScore());
                })
                .filter(e -> !interactedEventIds.contains(e.getKey()))
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(maxResults)
                .forEach(e -> responseObserver.onNext(RecommendedEventProto.newBuilder()
                        .setEventId(e.getKey())
                        .setScore(e.getValue())
                        .build()));

        responseObserver.onCompleted();
    }

    @Override
    public void getInteractionsCount(InteractionsCountRequestProto request,
                                     StreamObserver<RecommendedEventProto> responseObserver) {
        log.debug("Getting interactions count for {} events", request.getEventIdCount());

        for (Long eventId : request.getEventIdList()) {
            Double totalWeight = actionRepository.sumWeightsByEventId(eventId);
            responseObserver.onNext(RecommendedEventProto.newBuilder()
                    .setEventId(eventId)
                    .setScore(totalWeight != null ? totalWeight : 0.0)
                    .build());
        }

        responseObserver.onCompleted();
    }
}
