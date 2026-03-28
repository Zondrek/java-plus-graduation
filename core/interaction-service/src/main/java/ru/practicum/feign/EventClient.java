package ru.practicum.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import ru.practicum.dto.event.EventInternalDto;
import ru.practicum.dto.event.EventShortDto;

import java.util.List;

@FeignClient(name = "event-service", fallback = EventClientFallback.class)
public interface EventClient {

    @GetMapping("/internal/events/{eventId}")
    EventInternalDto getEvent(@PathVariable Long eventId);

    @GetMapping("/internal/events")
    List<EventShortDto> getEvents(@RequestParam List<Long> ids);
}
