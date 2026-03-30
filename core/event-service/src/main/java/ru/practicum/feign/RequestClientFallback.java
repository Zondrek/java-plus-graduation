package ru.practicum.feign;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.practicum.dto.request.ParticipationRequestDto;
import ru.practicum.enumeration.ParticipationStatus;

import java.util.Collections;
import java.util.List;

@Component
@Slf4j
public class RequestClientFallback implements RequestClient {
    @Override
    public long getConfirmedCount(Long eventId) {
        log.warn("request-service unavailable, returning 0 for eventId={}", eventId);
        return 0;
    }

    @Override
    public List<ParticipationRequestDto> getRequestsByEventId(Long eventId) {
        log.warn("request-service unavailable, returning empty list for eventId={}", eventId);
        return Collections.emptyList();
    }

    @Override
    public List<ParticipationRequestDto> getRequestsByEventIdAndIds(Long eventId, List<Long> requestIds) {
        log.warn("request-service unavailable, returning empty list");
        return Collections.emptyList();
    }

    @Override
    public void bulkUpdateStatus(Long eventId, List<Long> requestIds, ParticipationStatus status) {
        log.warn("request-service unavailable, cannot update request status");
    }

    @Override
    public void rejectAllPending(Long eventId) {
        log.warn("request-service unavailable, cannot reject pending requests");
    }
}
