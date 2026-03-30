package ru.practicum.feign;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.practicum.dto.event.EventInternalDto;

@Component
@Slf4j
public class EventClientFallback implements EventClient {

    @Override
    public EventInternalDto getEvent(Long eventId) {
        log.warn("event-service unavailable for eventId={}, event validation is critical", eventId);
        throw new RuntimeException("event-service недоступен. Невозможно проверить событие с ID: " + eventId);
    }
}
