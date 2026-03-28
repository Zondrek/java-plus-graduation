package ru.practicum.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import ru.practicum.dto.request.ParticipationRequestDto;
import ru.practicum.enumeration.ParticipationStatus;
import ru.practicum.mapper.ParticipationRequestMapper;
import ru.practicum.repository.ParticipationRequestRepository;

import java.util.List;

@RestController
@RequestMapping("/internal/requests")
@RequiredArgsConstructor
public class InternalRequestController {

    private final ParticipationRequestRepository requestRepository;
    private final ParticipationRequestMapper mapper;

    @GetMapping("/count")
    public long getConfirmedCount(@RequestParam Long eventId) {
        return requestRepository.countByEventIdAndStatus(eventId, ParticipationStatus.CONFIRMED);
    }

    @GetMapping
    public List<ParticipationRequestDto> getRequestsByEventId(@RequestParam Long eventId) {
        return requestRepository.findAllByEventId(eventId)
                .stream()
                .map(mapper::toDto)
                .toList();
    }

    @GetMapping("/by-event-and-ids")
    public List<ParticipationRequestDto> getRequestsByEventIdAndIds(
            @RequestParam Long eventId,
            @RequestParam List<Long> requestIds) {
        return requestRepository.findAllByEventIdAndIdIn(eventId, requestIds)
                .stream()
                .map(mapper::toDto)
                .toList();
    }

    @PatchMapping("/bulk-update")
    public void bulkUpdateStatus(
            @RequestParam Long eventId,
            @RequestParam List<Long> requestIds,
            @RequestParam ParticipationStatus status) {
        requestRepository.bulkUpdateStatus(eventId, requestIds, status);
    }

    @PatchMapping("/reject-pending")
    public void rejectAllPending(@RequestParam Long eventId) {
        requestRepository.rejectAllPendingRequests(eventId, ParticipationStatus.REJECTED);
    }

    @GetMapping("/exists")
    public boolean participationExists(
            @RequestParam Long userId,
            @RequestParam Long eventId,
            @RequestParam ParticipationStatus status) {
        return requestRepository.existsByRequesterIdAndEventIdAndStatus(userId, eventId, status);
    }
}
