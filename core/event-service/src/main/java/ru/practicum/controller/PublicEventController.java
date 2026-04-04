package ru.practicum.controller;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import ru.practicum.dto.event.EventFullDto;
import ru.practicum.dto.event.EventShortDto;
import ru.practicum.enumeration.EventSort;
import ru.practicum.service.EventService;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Публичный API для работы с событиями
 */
@RestController
@RequestMapping("/events")
@RequiredArgsConstructor
@Slf4j
@Validated
public class PublicEventController {

    private final EventService eventService;

    /**
     * Получение событий с возможностью фильтрации
     */
    @GetMapping
    public List<EventShortDto> getEvents(
            @RequestParam(required = false) String text,
            @RequestParam(required = false) List<Long> categories,
            @RequestParam(required = false) Boolean paid,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime rangeStart,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime rangeEnd,
            @RequestParam(defaultValue = "false") Boolean onlyAvailable,
            @RequestParam(required = false) EventSort sort,
            @RequestParam(defaultValue = "0") @PositiveOrZero Integer from,
            @RequestParam(defaultValue = "10") @Positive Integer size) {

        log.info("GET /events: text={}, categories={}, paid={}, rangeStart={}, rangeEnd={}, " +
                "onlyAvailable={}, sort={}, from={}, size={}",
                text, categories, paid, rangeStart, rangeEnd, onlyAvailable, sort, from, size);

        return eventService.getPublicEvents(text, categories, paid, rangeStart, rangeEnd,
                onlyAvailable, sort, from, size);
    }

    /**
     * Получение подробной информации об опубликованном событии по его идентификатору
     */
    @GetMapping("/{id}")
    public EventFullDto getEventById(
            @PathVariable @Min(1) Long id,
            @RequestHeader("X-EWM-USER-ID") long userId) {

        log.info("GET /events/{}: id={}, userId={}", id, id, userId);

        return eventService.getPublicEventById(id, userId);
    }

    /**
     * Получение рекомендаций событий для пользователя
     */
    @GetMapping("/recommendations")
    public List<EventShortDto> getRecommendations(
            @RequestHeader("X-EWM-USER-ID") long userId,
            @RequestParam(defaultValue = "10") int maxResults) {

        log.info("GET /events/recommendations: userId={}, maxResults={}", userId, maxResults);

        return eventService.getRecommendations(userId, maxResults);
    }

    /**
     * Поставить лайк событию
     */
    @PutMapping("/{eventId}/like")
    public ResponseEntity<Void> likeEvent(
            @PathVariable long eventId,
            @RequestHeader("X-EWM-USER-ID") long userId) {

        log.info("PUT /events/{}/like: userId={}", eventId, userId);

        eventService.likeEvent(userId, eventId);
        return ResponseEntity.noContent().build();
    }
}
