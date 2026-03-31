package ru.practicum.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import ru.practicum.dto.event.EventInternalDto;
import ru.practicum.dto.event.EventShortDto;
import ru.practicum.service.EventService;

import java.util.List;

@RestController
@RequestMapping("/internal/events")
@RequiredArgsConstructor
public class InternalEventController {
    private final EventService eventService;

    @GetMapping("/{eventId}")
    public EventInternalDto getEvent(@PathVariable Long eventId) {
        return eventService.getEventInternal(eventId);
    }

    @GetMapping
    public List<EventShortDto> getEvents(@RequestParam List<Long> ids) {
        return eventService.getEventsByIds(ids);
    }

    @PostMapping("/{eventId}/confirmed-requests")
    public void updateConfirmedRequests(@PathVariable Long eventId, @RequestParam int delta) {
        eventService.updateConfirmedRequests(eventId, delta);
    }
}
