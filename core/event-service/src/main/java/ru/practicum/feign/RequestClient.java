package ru.practicum.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import ru.practicum.dto.request.ParticipationRequestDto;
import ru.practicum.enumeration.ParticipationStatus;

import java.util.List;

@FeignClient(name = "request-service", fallback = RequestClientFallback.class)
public interface RequestClient {
    @GetMapping("/internal/requests/count")
    long getConfirmedCount(@RequestParam Long eventId);

    @GetMapping("/internal/requests")
    List<ParticipationRequestDto> getRequestsByEventId(@RequestParam Long eventId);

    @GetMapping("/internal/requests/by-event-and-ids")
    List<ParticipationRequestDto> getRequestsByEventIdAndIds(
            @RequestParam Long eventId, @RequestParam List<Long> requestIds);

    @PostMapping("/internal/requests/bulk-update")
    void bulkUpdateStatus(@RequestParam Long eventId,
                          @RequestParam List<Long> requestIds,
                          @RequestParam ParticipationStatus status);

    @PostMapping("/internal/requests/reject-pending")
    void rejectAllPending(@RequestParam Long eventId);

    @GetMapping("/internal/requests/exists")
    boolean participationExists(@RequestParam Long userId,
                                @RequestParam Long eventId,
                                @RequestParam ParticipationStatus status);
}
