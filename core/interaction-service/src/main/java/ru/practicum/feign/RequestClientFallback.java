package ru.practicum.feign;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.practicum.enumeration.ParticipationStatus;

@Component
@Slf4j
public class RequestClientFallback implements RequestClient {

    @Override
    public boolean participationExists(Long userId, Long eventId, ParticipationStatus status) {
        log.warn("request-service unavailable, returning false for userId={}, eventId={}", userId, eventId);
        return false;
    }
}
