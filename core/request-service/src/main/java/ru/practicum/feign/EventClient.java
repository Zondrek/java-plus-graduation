package ru.practicum.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import ru.practicum.dto.event.EventInternalDto;

@FeignClient(name = "event-service", fallback = EventClientFallback.class)
public interface EventClient {

    @GetMapping("/internal/events/{eventId}")
    EventInternalDto getEvent(@PathVariable Long eventId);

    @PostMapping("/internal/events/{eventId}/confirmed-requests")
    void updateConfirmedRequests(@PathVariable Long eventId, @RequestParam int delta);
}
