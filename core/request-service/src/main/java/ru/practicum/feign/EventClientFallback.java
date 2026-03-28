package ru.practicum.feign;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.practicum.dto.event.EventInternalDto;

@Component
@Slf4j
public class EventClientFallback implements EventClient {

    @Override
    public EventInternalDto getEvent(Long eventId) {
        log.error("event-service unavailable, cannot retrieve event data for eventId={}", eventId);
        throw new RuntimeException("event-service unavailable, cannot retrieve event with id=" + eventId);
    }

    @Override
    public void updateConfirmedRequests(Long eventId, int delta) {
        log.error("event-service unavailable, cannot update confirmedRequests for eventId={}", eventId);
        throw new RuntimeException("event-service unavailable, cannot update confirmedRequests for event with id=" + eventId);
    }
}
