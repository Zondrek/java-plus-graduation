package ru.practicum.service;

import com.querydsl.core.BooleanBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.AnalyzerClient;
import ru.practicum.CollectorClient;
import ru.practicum.dto.event.*;
import ru.practicum.dto.request.EventRequestStatusUpdateRequest;
import ru.practicum.dto.request.EventRequestStatusUpdateResult;
import ru.practicum.dto.request.ParticipationRequestDto;
import ru.practicum.dto.user.UserDto;
import ru.practicum.dto.user.UserShortDto;
import ru.practicum.enumeration.EventSort;
import ru.practicum.enumeration.EventState;
import ru.practicum.enumeration.ParticipationStatus;
import ru.practicum.enumeration.StateAction;
import ru.practicum.exception.BadRequestException;
import ru.practicum.exception.ConflictException;
import ru.practicum.exception.NotFoundException;
import ru.practicum.feign.RequestClient;
import ru.practicum.feign.UserClient;
import ru.practicum.mapper.EventMapper;
import ru.practicum.model.Category;
import ru.practicum.model.Event;
import ru.practicum.model.QEvent;
import ru.practicum.repository.CategoryRepository;
import ru.practicum.repository.EventRepository;
import stats.service.collector.ActionTypeProto;
import stats.service.dashboard.RecommendedEventProto;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Сервис для работы с событиями
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class EventService {

    private final EventRepository eventRepository;
    private final CategoryRepository categoryRepository;
    private final EventMapper eventMapper;
    private final CollectorClient collectorClient;
    private final AnalyzerClient analyzerClient;
    private final UserClient userClient;
    private final RequestClient requestClient;

    private static final int MIN_HOURS_BEFORE_EVENT = 2;

    /**
     * Получить публичные события с фильтрацией (только опубликованные)
     */
    public List<EventShortDto> getPublicEvents(
            String text,
            List<Long> categories,
            Boolean paid,
            LocalDateTime rangeStart,
            LocalDateTime rangeEnd,
            Boolean onlyAvailable,
            EventSort sort,
            Integer from,
            Integer size) {

        // Валидация: rangeEnd должен быть позже rangeStart
        if (rangeStart != null && rangeEnd != null && rangeEnd.isBefore(rangeStart)) {
            throw new BadRequestException("Дата окончания не может быть раньше даты начала");
        }

        BooleanBuilder predicate = new BooleanBuilder();

        // Только опубликованные события
        predicate.and(QEvent.event.state.eq(EventState.PUBLISHED));

        // Текстовый поиск (без учета регистра)
        if (text != null && !text.isBlank()) {
            predicate.and(QEvent.event.annotation.containsIgnoreCase(text)
                    .or(QEvent.event.description.containsIgnoreCase(text)));
        }

        // Фильтр по категориям
        if (categories != null && !categories.isEmpty()) {
            predicate.and(QEvent.event.category.id.in(categories));
        }

        // Фильтр платные/бесплатные
        if (paid != null) {
            predicate.and(QEvent.event.paid.eq(paid));
        }

        // Фильтр по датам
        LocalDateTime start = rangeStart != null ? rangeStart : LocalDateTime.now();
        predicate.and(QEvent.event.eventDate.after(start));

        if (rangeEnd != null) {
            predicate.and(QEvent.event.eventDate.before(rangeEnd));
        }

        // Фильтр только доступные (не исчерпан лимит)
        if (onlyAvailable != null && onlyAvailable) {
            predicate.and(QEvent.event.participantLimit.eq(0)
                    .or(QEvent.event.confirmedRequests.lt(QEvent.event.participantLimit)));
        }

        // Пагинация
        Pageable pageable = PageRequest.of(from / size, size);
        List<Event> eventsList = StreamSupport.stream(
                        eventRepository.findAll(predicate, pageable).spliterator(), false)
                .toList();

        // Получение рейтинга из analyzer
        Map<Long, Double> ratingsMap = new HashMap<>();
        if (!eventsList.isEmpty()) {
            List<Long> eventIds = eventsList.stream()
                    .map(Event::getId)
                    .collect(Collectors.toList());

            analyzerClient.getInteractionsCount(eventIds).forEach(proto ->
                    ratingsMap.put(proto.getEventId(), proto.getScore())
            );
        }

        // Преобразование в DTO с проставлением рейтинга и инициатора
        List<EventShortDto> result = eventsList.stream()
                .map(event -> {
                    EventShortDto dto = eventMapper.toShortDto(event);
                    dto.setRating(ratingsMap.getOrDefault(event.getId(), 0.0));
                    return dto;
                })
                .collect(Collectors.toList());
        enrichShortDtosWithUsers(result, eventsList);

        // Сортировка по рейтингу, если указана
        if (sort == EventSort.VIEWS) {
            result.sort(Comparator.comparing(EventShortDto::getRating).reversed());
        }

        log.info("Найдено {} публичных событий", result.size());
        return result;
    }

    /**
     * Получить публичное событие по ID
     */
    public EventFullDto getPublicEventById(Long id, long userId) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Событие не найдено с ID: " + id));

        if (event.getState() != EventState.PUBLISHED) {
            throw new NotFoundException("Событие не опубликовано");
        }

        // Отправка действия просмотра в collector
        collectorClient.collectUserAction(userId, id, ActionTypeProto.ACTION_VIEW);

        // Получение рейтинга из analyzer
        double rating = analyzerClient.getInteractionsCount(List.of(id))
                .findFirst()
                .map(RecommendedEventProto::getScore)
                .orElse(0.0);

        EventFullDto result = eventMapper.toFullDto(event);
        result.setRating(rating);
        enrichSingleDtoWithUser(result, event.getInitiatorId());

        log.info("Получено публичное событие с ID: {}, рейтинг: {}", id, rating);
        return result;
    }

    /**
     * Получить рекомендации для пользователя
     */
    public List<EventShortDto> getRecommendations(long userId, int maxResults) {
        List<RecommendedEventProto> recommendations = analyzerClient
                .getRecommendationsForUser(userId, maxResults)
                .collect(Collectors.toList());

        if (recommendations.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> eventIds = recommendations.stream()
                .map(RecommendedEventProto::getEventId)
                .collect(Collectors.toList());

        Map<Long, Double> ratingsMap = recommendations.stream()
                .collect(Collectors.toMap(RecommendedEventProto::getEventId, RecommendedEventProto::getScore));

        List<Event> events = eventRepository.findAllByIdIn(eventIds);
        List<EventShortDto> result = events.stream()
                .map(event -> {
                    EventShortDto dto = eventMapper.toShortDto(event);
                    dto.setRating(ratingsMap.getOrDefault(event.getId(), 0.0));
                    return dto;
                })
                .collect(Collectors.toList());
        enrichShortDtosWithUsers(result, events);

        log.info("Получено {} рекомендаций для пользователя {}", result.size(), userId);
        return result;
    }

    /**
     * Поставить лайк событию
     */
    @Transactional
    public void likeEvent(long userId, long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Событие не найдено с ID: " + eventId));

        if (event.getState() != EventState.PUBLISHED) {
            throw new NotFoundException("Событие не опубликовано");
        }

        if (!requestClient.participationExists(userId, eventId, ParticipationStatus.CONFIRMED)) {
            throw new BadRequestException("Пользователь не посещал мероприятие с ID: " + eventId);
        }

        collectorClient.collectUserAction(userId, eventId, ActionTypeProto.ACTION_LIKE);
        log.info("Пользователь {} поставил лайк событию {}", userId, eventId);
    }

    /**
     * Получить события текущего пользователя
     */
    public List<EventShortDto> getUserEvents(Long userId, Integer from, Integer size) {
        // Verify user exists via Feign (fallback returns placeholder so no exception)
        userClient.getUser(userId);

        BooleanBuilder predicate = new BooleanBuilder();
        predicate.and(QEvent.event.initiatorId.eq(userId));

        Pageable pageable = PageRequest.of(from / size, size);
        Iterable<Event> events = eventRepository.findAll(predicate, pageable);

        List<Event> eventsList = StreamSupport.stream(events.spliterator(), false)
                .collect(Collectors.toList());
        List<EventShortDto> result = eventsList.stream()
                .map(eventMapper::toShortDto)
                .collect(Collectors.toList());
        enrichShortDtosWithUsers(result, eventsList);

        log.info("Получено {} событий пользователя {}", result.size(), userId);
        return result;
    }

    /**
     * Добавить новое событие
     */
    @Transactional
    public EventFullDto addEvent(Long userId, NewEventDto newEventDto) {
        UserDto user = userClient.getUser(userId);

        Category category = categoryRepository.findById(newEventDto.getCategory())
                .orElseThrow(() -> new NotFoundException("Категория не найдена с ID: " + newEventDto.getCategory()));

        // Валидация: дата события должна быть не раньше чем через 2 часа
        if (newEventDto.getEventDate().isBefore(LocalDateTime.now().plusHours(MIN_HOURS_BEFORE_EVENT))) {
            throw new BadRequestException("Дата события должна быть не раньше чем через " +
                    MIN_HOURS_BEFORE_EVENT + " часа от текущего момента");
        }

        Event event = eventMapper.toEntity(newEventDto);
        event.setInitiatorId(userId);
        event.setCategory(category);
        event.setCreatedOn(LocalDateTime.now());
        event.setState(EventState.PENDING);
        event.setConfirmedRequests(0);

        event = eventRepository.save(event);
        log.info("Создано новое событие с ID: {}", event.getId());

        EventFullDto result = eventMapper.toFullDto(event);
        result.setInitiator(UserShortDto.builder().id(user.getId()).name(user.getName()).build());
        return result;
    }

    /**
     * Получить событие пользователя по ID
     */
    public EventFullDto getUserEventById(Long userId, Long eventId) {
        userClient.getUser(userId);

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Событие не найдено с ID: " + eventId));

        if (!event.getInitiatorId().equals(userId)) {
            throw new NotFoundException("Событие не принадлежит пользователю");
        }

        EventFullDto result = eventMapper.toFullDto(event);
        enrichSingleDtoWithUser(result, event.getInitiatorId());
        return result;
    }

    /**
     * Обновить событие пользователя
     */
    @Transactional
    public EventFullDto updateUserEvent(Long userId, Long eventId, UpdateEventUserRequest updateRequest) {
        userClient.getUser(userId);

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Событие не найдено с ID: " + eventId));

        if (!event.getInitiatorId().equals(userId)) {
            throw new ConflictException("Событие не принадлежит пользователю");
        }

        // Можно изменить только отмененные события или события в состоянии ожидания модерации
        if (event.getState() == EventState.PUBLISHED) {
            throw new ConflictException("Нельзя изменить опубликованное событие");
        }

        // Валидация даты
        if (updateRequest.getEventDate() != null &&
                updateRequest.getEventDate().isBefore(LocalDateTime.now().plusHours(MIN_HOURS_BEFORE_EVENT))) {
            throw new BadRequestException("Дата события должна быть не раньше чем через " +
                    MIN_HOURS_BEFORE_EVENT + " часа от текущего момента");
        }

        // Обновление категории если указана
        if (updateRequest.getCategory() != null) {
            Category category = categoryRepository.findById(updateRequest.getCategory())
                    .orElseThrow(() -> new NotFoundException("Категория не найдена с ID: " + updateRequest.getCategory()));
            event.setCategory(category);
        }

        // Обновление состояния
        if (updateRequest.getStateAction() != null) {
            if (updateRequest.getStateAction() == StateAction.SEND_TO_REVIEW) {
                event.setState(EventState.PENDING);
            } else if (updateRequest.getStateAction() == StateAction.CANCEL_REVIEW) {
                event.setState(EventState.CANCELED);
            }
        }

        // Обновление остальных полей
        eventMapper.updateEventFromUserRequest(updateRequest, event);

        event = eventRepository.save(event);
        log.info("Обновлено событие с ID: {}", eventId);

        EventFullDto result = eventMapper.toFullDto(event);
        enrichSingleDtoWithUser(result, event.getInitiatorId());
        return result;
    }

    /**
     * Получить события администратором с фильтрацией
     */
    public List<EventFullDto> getAdminEvents(
            List<Long> users,
            List<EventState> states,
            List<Long> categories,
            LocalDateTime rangeStart,
            LocalDateTime rangeEnd,
            Integer from,
            Integer size) {

        BooleanBuilder predicate = new BooleanBuilder();

        // Фильтр по пользователям
        if (users != null && !users.isEmpty()) {
            predicate.and(QEvent.event.initiatorId.in(users));
        }

        // Фильтр по состояниям
        if (states != null && !states.isEmpty()) {
            predicate.and(QEvent.event.state.in(states));
        }

        // Фильтр по категориям
        if (categories != null && !categories.isEmpty()) {
            predicate.and(QEvent.event.category.id.in(categories));
        }

        // Фильтр по датам
        if (rangeStart != null) {
            predicate.and(QEvent.event.eventDate.after(rangeStart));
        }

        if (rangeEnd != null) {
            predicate.and(QEvent.event.eventDate.before(rangeEnd));
        }

        Pageable pageable = PageRequest.of(from / size, size);
        Iterable<Event> events = eventRepository.findAll(predicate, pageable);

        List<Event> eventsList = StreamSupport.stream(events.spliterator(), false)
                .collect(Collectors.toList());
        List<EventFullDto> result = eventsList.stream()
                .map(eventMapper::toFullDto)
                .collect(Collectors.toList());
        enrichFullDtosWithUsers(result, eventsList);

        log.info("Администратор получил {} событий", result.size());
        return result;
    }

    /**
     * Обновить событие администратором
     */
    @Transactional
    public EventFullDto updateEventByAdmin(Long eventId, UpdateEventAdminRequest updateRequest) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Событие не найдено с ID: " + eventId));

        // Валидация даты: должна быть не ранее чем за час от даты публикации
        if (updateRequest.getEventDate() != null) {
            if (updateRequest.getEventDate().isBefore(LocalDateTime.now().plusHours(1))) {
                throw new BadRequestException("Дата начала события должна быть не ранее чем за час от даты публикации");
            }
        }

        // Обновление категории если указана
        if (updateRequest.getCategory() != null) {
            Category category = categoryRepository.findById(updateRequest.getCategory())
                    .orElseThrow(() -> new NotFoundException("Категория не найдена с ID: " + updateRequest.getCategory()));
            event.setCategory(category);
        }

        // Обновление состояния
        if (updateRequest.getStateAction() != null) {
            if (updateRequest.getStateAction() == StateAction.PUBLISH_EVENT) {
                if (event.getState() != EventState.PENDING) {
                    throw new ConflictException("Можно публиковать только события в состоянии ожидания публикации");
                }
                event.setState(EventState.PUBLISHED);
                event.setPublishedOn(LocalDateTime.now());
            } else if (updateRequest.getStateAction() == StateAction.REJECT_EVENT) {
                if (event.getState() == EventState.PUBLISHED) {
                    throw new ConflictException("Нельзя отклонить опубликованное событие");
                }
                event.setState(EventState.CANCELED);
            }
        }

        // Обновление остальных полей
        eventMapper.updateEventFromAdminRequest(updateRequest, event);

        event = eventRepository.save(event);
        log.info("Администратор обновил событие с ID: {}", eventId);

        EventFullDto result = eventMapper.toFullDto(event);
        enrichSingleDtoWithUser(result, event.getInitiatorId());
        return result;
    }

    @Transactional
    public EventRequestStatusUpdateResult updateRequestsStatus(
            Long userId, Long eventId, EventRequestStatusUpdateRequest request) {

        if (request.getStatus() == ParticipationStatus.PENDING) {
            throw new BadRequestException("Статус 'PENDING' не может быть установлен для заявок с ID: " + request.getRequestIds());
        }

        List<Long> requestIds = request.getRequestIds();

        // 1. Проверяем существование события
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event with id=" + eventId));

        // 2. Проверяем пре-модерацию и лимит
        boolean preModeration = event.getRequestModeration();
        int maxLimit = event.getParticipantLimit();

        if (maxLimit == 0 || !preModeration) {
            throw new ConflictException(
                    "Pre-moderation is disabled or limit is 0, no status change needed");
        }

        // 3. Проверяем, что пользователь — инициатор события
        if (!userId.equals(event.getInitiatorId())) {
            throw new ConflictException("User is not event initiator");
        }

        // 4. Получаем заявки для обновления через Feign
        List<ParticipationRequestDto> requests = requestClient.getRequestsByEventIdAndIds(eventId, requestIds);

        if (requests.isEmpty()) {
            throw new NotFoundException("No requests found for the given IDs");
        }

        // 5. Проверяем, что все заявки в статусе PENDING
        for (ParticipationRequestDto req : requests) {
            if (req.getStatus() != ParticipationStatus.PENDING) {
                throw new ConflictException(
                        "Request " + req.getId() + " is not in PENDING status");
            }
        }

        // 6. Проверяем лимит подтверждённых заявок
        long confirmedCount = requestClient.getConfirmedCount(eventId);

        if (confirmedCount >= maxLimit) {
            throw new ConflictException("Participant limit reached");
        }

        // 7. Обновляем статус выбранных заявок
        long currentConfirmed = confirmedCount;

        if (request.getStatus() == ParticipationStatus.CONFIRMED) {
            currentConfirmed += requestIds.size();
        }

        // 8. Проверяем лимит и обновляем статусы через Feign
        if (currentConfirmed >= maxLimit) {
            long canConfirm = maxLimit - confirmedCount;

            List<Long> requestIdsPart = requestIds.stream()
                    .limit(canConfirm)
                    .collect(Collectors.toList());

            requestClient.bulkUpdateStatus(eventId, requestIdsPart, request.getStatus());

            // Отклоняем оставшиеся PENDING заявки
            requestClient.rejectAllPending(eventId);

            // УВЕЛИЧИВАЕМ confirmedRequests у события
            event.setConfirmedRequests(Math.min(maxLimit, (int) currentConfirmed));
            eventRepository.save(event);

        } else {
            requestClient.bulkUpdateStatus(eventId, requestIds, request.getStatus());

            // УВЕЛИЧИВАЕМ confirmedRequests на число подтверждённых заявок
            event.setConfirmedRequests((int) (confirmedCount + requestIds.size()));
            eventRepository.save(event);
        }

        // 9. Формируем ответ — получаем актуальные данные заявок
        List<ParticipationRequestDto> updatedRequests = requestClient.getRequestsByEventIdAndIds(eventId, requestIds);

        List<ParticipationRequestDto> confirmed = updatedRequests.stream()
                .filter(r -> r.getStatus() == ParticipationStatus.CONFIRMED)
                .collect(Collectors.toList());

        List<ParticipationRequestDto> rejected = updatedRequests.stream()
                .filter(r -> r.getStatus() == ParticipationStatus.REJECTED)
                .collect(Collectors.toList());

        return EventRequestStatusUpdateResult.builder()
                .confirmedRequests(confirmed)
                .rejectedRequests(rejected)
                .build();
    }

    public List<ParticipationRequestDto> getEventParticipantRequests(Long userId, Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> {
                    log.warn("Event with ID {} not found", eventId);
                    return new NotFoundException("Event with id: " + eventId + " was not found");
                });

        if (!event.getInitiatorId().equals(userId)) {
            throw new ConflictException("User with id = " + userId + " is not event initiator");
        }

        return requestClient.getRequestsByEventId(eventId);
    }

    /**
     * Получить внутреннее DTO события (для inter-service API)
     */
    public EventInternalDto getEventInternal(Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event not found: " + eventId));
        return EventInternalDto.builder()
                .id(event.getId())
                .initiatorId(event.getInitiatorId())
                .categoryId(event.getCategory().getId())
                .state(event.getState())
                .participantLimit(event.getParticipantLimit())
                .confirmedRequests(event.getConfirmedRequests())
                .requestModeration(event.getRequestModeration())
                .publishedOn(event.getPublishedOn())
                .title(event.getTitle())
                .build();
    }

    /**
     * Получить краткие DTO событий по списку ID
     */
    public List<EventShortDto> getEventsByIds(List<Long> ids) {
        List<Event> events = eventRepository.findAllByIdIn(ids);
        // Batch fetch users for initiators
        List<Long> initiatorIds = events.stream().map(Event::getInitiatorId).distinct().toList();
        Map<Long, UserDto> usersMap = new HashMap<>();
        try {
            List<UserDto> users = userClient.getUsers(initiatorIds);
            usersMap = users.stream().collect(Collectors.toMap(UserDto::getId, u -> u));
        } catch (Exception e) {
            log.warn("Failed to fetch users for events", e);
        }
        Map<Long, UserDto> finalUsersMap = usersMap;
        return events.stream().map(event -> {
            EventShortDto dto = eventMapper.toShortDto(event);
            UserDto user = finalUsersMap.get(event.getInitiatorId());
            if (user != null) {
                dto.setInitiator(UserShortDto.builder().id(user.getId()).name(user.getName()).build());
            }
            return dto;
        }).collect(Collectors.toList());
    }

    /**
     * Обновить количество подтверждённых заявок
     */
    @Transactional
    public void updateConfirmedRequests(Long eventId, int delta) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event not found: " + eventId));
        event.setConfirmedRequests(event.getConfirmedRequests() + delta);
        eventRepository.save(event);
    }

    // -------- private helpers --------

    private Map<Long, UserShortDto> fetchUsersMap(List<Long> initiatorIds) {
        List<Long> uniqueIds = initiatorIds.stream().distinct().toList();
        Map<Long, UserShortDto> usersMap = new java.util.HashMap<>();
        try {
            List<UserDto> users = userClient.getUsers(uniqueIds);
            for (UserDto u : users) {
                usersMap.put(u.getId(), UserShortDto.builder().id(u.getId()).name(u.getName()).build());
            }
        } catch (Exception e) {
            log.warn("Failed to batch-fetch users for event enrichment", e);
        }
        return usersMap;
    }

    private void enrichShortDtosWithUsers(List<EventShortDto> dtos, List<Event> events) {
        List<Long> initiatorIds = events.stream().map(Event::getInitiatorId).toList();
        Map<Long, UserShortDto> usersMap = fetchUsersMap(initiatorIds);
        for (int i = 0; i < dtos.size(); i++) {
            UserShortDto user = usersMap.get(events.get(i).getInitiatorId());
            if (user != null) {
                dtos.get(i).setInitiator(user);
            }
        }
    }

    private void enrichFullDtosWithUsers(List<EventFullDto> dtos, List<Event> events) {
        List<Long> initiatorIds = events.stream().map(Event::getInitiatorId).toList();
        Map<Long, UserShortDto> usersMap = fetchUsersMap(initiatorIds);
        for (int i = 0; i < dtos.size(); i++) {
            UserShortDto user = usersMap.get(events.get(i).getInitiatorId());
            if (user != null) {
                dtos.get(i).setInitiator(user);
            }
        }
    }

    private void enrichSingleDtoWithUser(EventFullDto dto, Long initiatorId) {
        Map<Long, UserShortDto> usersMap = fetchUsersMap(List.of(initiatorId));
        UserShortDto user = usersMap.get(initiatorId);
        if (user != null) {
            dto.setInitiator(user);
        }
    }
}
