package ru.practicum.feign;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.practicum.dto.event.EventInternalDto;
import ru.practicum.dto.event.EventShortDto;

import java.util.Collections;
import java.util.List;

@Component
@Slf4j
public class EventClientFallback implements EventClient {

    @Override
    public EventInternalDto getEvent(Long eventId) {
        log.warn("event-service unavailable for eventId={}", eventId);
        throw new RuntimeException("event-service unavailable, cannot retrieve event with id=" + eventId);
    }

    @Override
    public List<EventShortDto> getEvents(List<Long> ids) {
        log.warn("event-service unavailable, returning empty list for ids={}", ids);
        return Collections.emptyList();
    }
}
