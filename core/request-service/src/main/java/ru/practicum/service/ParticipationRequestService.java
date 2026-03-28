package ru.practicum.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.dto.event.EventInternalDto;
import ru.practicum.dto.request.ParticipationRequestDto;
import ru.practicum.enumeration.ParticipationStatus;
import ru.practicum.exception.ConflictException;
import ru.practicum.exception.NotFoundException;
import ru.practicum.feign.EventClient;
import ru.practicum.feign.UserClient;
import ru.practicum.mapper.ParticipationRequestMapper;
import ru.practicum.model.ParticipationRequest;
import ru.practicum.repository.ParticipationRequestRepository;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ParticipationRequestService {

    private final ParticipationRequestRepository requestRepository;
    private final UserClient userClient;
    private final EventClient eventClient;
    private final ParticipationRequestMapper mapper;

    @Transactional
    public ParticipationRequestDto addRequest(Long userId, Long eventId) {
        log.info("Creating a request for event: {} by user: {}", eventId, userId);

        // Validate user exists
        try {
            userClient.getUser(userId);
        } catch (Exception e) {
            log.warn("User with ID {} not found", userId);
            throw new NotFoundException("User with id: " + userId + " was not found");
        }

        // Fetch event data
        EventInternalDto event;
        try {
            event = eventClient.getEvent(eventId);
        } catch (Exception e) {
            log.warn("Event with ID {} not found", eventId);
            throw new NotFoundException("Event with id: " + eventId + " was not found");
        }

        if (event.getInitiatorId().equals(userId)) {
            throw new ConflictException("User cannot request participation in their own event");
        }

        if (event.getPublishedOn() == null) {
            throw new ConflictException("Cannot participate in an unpublished event");
        }

        Integer participantLimit = event.getParticipantLimit();
        Integer confirmedRequests = event.getConfirmedRequests();

        if (participantLimit != 0 && confirmedRequests >= participantLimit) {
            throw new ConflictException("Participation request limit reached for event id=" + eventId);
        }

        if (requestRepository.findByRequesterIdAndEventId(userId, eventId).isPresent()) {
            throw new ConflictException("Duplicate participation request");
        }

        ParticipationRequest request = new ParticipationRequest();
        request.setRequesterId(userId);
        request.setEventId(eventId);
        request.setCreated(LocalDateTime.now());

        if (Boolean.FALSE.equals(event.getRequestModeration()) || participantLimit == 0) {
            request.setStatus(ParticipationStatus.CONFIRMED);
        } else {
            request.setStatus(ParticipationStatus.PENDING);
        }

        ParticipationRequest savedRequest = requestRepository.save(request);

        if (savedRequest.getStatus() == ParticipationStatus.CONFIRMED) {
            eventClient.updateConfirmedRequests(eventId, 1);
            log.info("Updated confirmedRequests for event {} by delta 1", eventId);
        }

        log.info("The request was successfully created: {}", savedRequest);
        return mapper.toDto(savedRequest);
    }

    @Transactional
    public ParticipationRequestDto cancelRequest(Long userId, Long requestId) {
        log.info("Creating a cancellation request: {} for event by user: {}", requestId, userId);

        ParticipationRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new NotFoundException("Request with id: " + requestId + " was not found"));

        // Validate user exists
        try {
            userClient.getUser(userId);
        } catch (Exception e) {
            log.warn("User with ID {} not found", userId);
            throw new NotFoundException("User with id: " + userId + " was not found");
        }

        if (!request.getRequesterId().equals(userId)) {
            throw new NotFoundException("Request with id=" + requestId + " does not belong to user " + userId);
        }

        request.setStatus(ParticipationStatus.CANCELED);
        ParticipationRequest savedRequest = requestRepository.save(request);
        log.info("The request: {} was successfully cancelled", savedRequest);
        return mapper.toDto(savedRequest);
    }

    public List<ParticipationRequestDto> getUserRequests(Long userId) {
        log.info("Searching event requests for the user with id: {}", userId);

        // Validate user exists
        try {
            userClient.getUser(userId);
        } catch (Exception e) {
            log.warn("User with ID {} not found", userId);
            throw new NotFoundException("User with id: " + userId + " was not found");
        }

        List<ParticipationRequestDto> requests = requestRepository.findAllByRequesterId(userId)
                .stream()
                .map(mapper::toDto)
                .toList();

        log.info("Event requests found: {}", requests.size());
        return requests;
    }
}
