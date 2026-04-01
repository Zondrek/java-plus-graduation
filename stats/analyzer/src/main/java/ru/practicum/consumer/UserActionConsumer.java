package ru.practicum.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import ru.practicum.ewm.stats.avro.UserActionAvro;
import ru.practicum.model.UserAction;
import ru.practicum.model.UserActionId;
import ru.practicum.repository.UserActionRepository;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Component
@Slf4j
@RequiredArgsConstructor
public class UserActionConsumer {

    private final UserActionRepository repository;

    @KafkaListener(topics = "stats.user-actions.v1", containerFactory = "userActionListenerFactory")
    public void consume(UserActionAvro action) {
        log.debug("Received user action: userId={}, eventId={}, type={}",
                action.getUserId(), action.getEventId(), action.getActionType());

        double weight = switch (action.getActionType()) {
            case VIEW -> 0.4;
            case REGISTER -> 0.8;
            case LIKE -> 1.0;
        };

        UserActionId id = new UserActionId(action.getUserId(), action.getEventId());
        LocalDateTime ts = LocalDateTime.ofInstant(action.getTimestamp(), ZoneOffset.UTC);

        repository.findById(id).ifPresentOrElse(
            existing -> {
                if (weight > existing.getMaxWeight()) {
                    existing.setMaxWeight(weight);
                    existing.setTimestamp(ts);
                    repository.save(existing);
                    log.debug("Updated user action weight: userId={}, eventId={}, weight={}",
                            id.getUserId(), id.getEventId(), weight);
                }
            },
            () -> {
                repository.save(UserAction.builder()
                        .id(id)
                        .maxWeight(weight)
                        .timestamp(ts)
                        .build());
                log.debug("Saved new user action: userId={}, eventId={}, weight={}",
                        id.getUserId(), id.getEventId(), weight);
            }
        );
    }
}
