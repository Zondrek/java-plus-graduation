package ru.practicum.service;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;
import ru.practicum.dto.compilation.CompilationDto;
import ru.practicum.dto.compilation.NewCompilationDto;
import ru.practicum.dto.compilation.UpdateCompilationRequest;
import ru.practicum.dto.event.EventShortDto;
import ru.practicum.dto.user.UserDto;
import ru.practicum.dto.user.UserShortDto;
import ru.practicum.exception.ConflictException;
import ru.practicum.exception.NotFoundException;
import ru.practicum.feign.UserClient;
import ru.practicum.mapper.CompilationMapper;
import ru.practicum.mapper.EventMapper;
import ru.practicum.model.Compilation;
import ru.practicum.model.Event;
import ru.practicum.repository.CompilationRepository;
import ru.practicum.repository.EventRepository;

import java.util.*;
import java.util.stream.Collectors;

import static ru.practicum.model.QCompilation.compilation;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CompilationService {

    private final CompilationRepository compRep;
    private final CompilationMapper mapper;
    private final EventRepository eventRepository;
    private final EventMapper eventMapper;
    private final UserClient userClient;
    private final JPAQueryFactory queryFactory;

    /**
     * Получение списка подборок с возможностью фильтрации по статусу закрепления
     */
    @Transactional(readOnly = true)
    public List<CompilationDto> findCompilations(Boolean pinned, Pageable pageable) {
        BooleanBuilder predicate = new BooleanBuilder();

        if (pinned == null) pinned = false;
        predicate.and(compilation.pinned.eq(pinned));

        // 1. Получаем IDs подборок с пагинацией
        List<Long> compilationIds = queryFactory
                .select(compilation.id)
                .from(compilation)
                .where(predicate)
                .orderBy(compilation.id.asc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        if (compilationIds.isEmpty()) {
            log.info("Нет подборок с статусом закрепления ={}", pinned);
            return Collections.emptyList();
        }

        // 2. Загружаем подборки по IDs (eventIds загрузятся через @ElementCollection)
        List<Compilation> compilations = compRep.findAllById(compilationIds);

        // 3. Собираем все уникальные eventIds из всех подборок
        Set<Long> allEventIds = compilations.stream()
                .flatMap(comp -> comp.getEventIds().stream())
                .collect(Collectors.toSet());

        // 4. Загружаем события напрямую из репозитория
        Map<Long, EventShortDto> eventsById = Collections.emptyMap();
        if (!allEventIds.isEmpty()) {
            List<EventShortDto> eventShortDtos = getEventShortDtos(new ArrayList<>(allEventIds));
            eventsById = eventShortDtos.stream()
                    .collect(Collectors.toMap(EventShortDto::getId, e -> e));
        }

        // 5. Раскладываем события по подборкам и сортируем по id
        final Map<Long, EventShortDto> eventsByIdFinal = eventsById;
        List<CompilationDto> result = compilations.stream()
                .sorted(Comparator.comparing(Compilation::getId))
                .map(comp -> {
                    CompilationDto dto = mapper.toDto(comp);
                    List<EventShortDto> events = comp.getEventIds().stream()
                            .map(eventsByIdFinal::get)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toList());
                    dto.setEvents(events);
                    return dto;
                })
                .collect(Collectors.toList());

        log.info("Получен список подборок событий размером: {}", result.size());
        return result;
    }

    /**
     * Поиск подборки по ID
     */
    @Transactional(readOnly = true)
    public CompilationDto findCompilationById(Long compId) {
        Compilation comp = compRep.findById(compId).orElseThrow(
                () -> new NotFoundException("Подборка с ID: " + compId + " не найдена."));

        CompilationDto dto = mapper.toDto(comp);

        if (!comp.getEventIds().isEmpty()) {
            List<EventShortDto> eventDtos = getEventShortDtos(new ArrayList<>(comp.getEventIds()));
            dto.setEvents(eventDtos);
        } else {
            dto.setEvents(Collections.emptyList());
        }

        log.info("Найдена подборка с ID: {}", compId);
        return dto;
    }

    /**
     * Сохранение подборки
     */
    public CompilationDto saveCompilation(NewCompilationDto newCompilationDto) {
        Compilation comp = mapper.toEntity(newCompilationDto);

        if (!ObjectUtils.isEmpty(newCompilationDto.getEvents())) {
            comp.setEventIds(new HashSet<>(newCompilationDto.getEvents()));
        }

        try {
            Compilation savedCompilation = compRep.save(comp);
            CompilationDto dto = mapper.toDto(savedCompilation);

            List<EventShortDto> eventShortDtos = Collections.emptyList();
            if (!savedCompilation.getEventIds().isEmpty()) {
                eventShortDtos = getEventShortDtos(new ArrayList<>(savedCompilation.getEventIds()));
            }
            dto.setEvents(eventShortDtos);

            log.info("Сохранена новая подборка с ID: {}", savedCompilation.getId());
            return dto;
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("Ошибка при сохранении новой подборки.");
        }
    }

    /**
     * Удаление подборки по ID
     */
    public void deleteCompilation(Long compId) {
        if (!compRep.existsById(compId)) {
            throw new NotFoundException("Подборка с ID: " + compId + " не найдена.");
        }
        compRep.deleteById(compId);
        log.info("Подборка с ID: {} удалена.", compId);
    }

    /**
     * Обновление подборки по ID
     */
    public CompilationDto updateCompilation(Long id, UpdateCompilationRequest updReqCompDto) {
        Compilation comp = compRep.findById(id)
                .orElseThrow(() -> new NotFoundException("Подборка с ID: " + id + " не найдена."));

        if (updReqCompDto.getPinned() != null && !comp.getPinned().equals(updReqCompDto.getPinned())) {
            comp.setPinned(updReqCompDto.getPinned());
        }
        if (updReqCompDto.getTitle() != null && !comp.getTitle().equals(updReqCompDto.getTitle())) {
            comp.setTitle(updReqCompDto.getTitle());
        }

        List<EventShortDto> eventDtos = null;

        if (updReqCompDto.getEvents() != null) {
            if (!updReqCompDto.getEvents().isEmpty()) {
                Set<Long> currentEventIds = comp.getEventIds();
                boolean allPresent = new HashSet<>(currentEventIds).containsAll(updReqCompDto.getEvents());

                if (!allPresent) {
                    Set<Long> newEventIds = new HashSet<>(updReqCompDto.getEvents());
                    comp.setEventIds(newEventIds);
                    eventDtos = getEventShortDtos(new ArrayList<>(newEventIds));
                }
            } else {
                comp.setEventIds(Collections.emptySet());
                eventDtos = Collections.emptyList();
            }
        }

        try {
            compRep.save(comp);
            CompilationDto dto = mapper.toDto(comp);

            if (eventDtos != null) {
                dto.setEvents(eventDtos);
            } else {
                // Загружаем текущие события
                List<EventShortDto> currentEvents = Collections.emptyList();
                if (!comp.getEventIds().isEmpty()) {
                    currentEvents = getEventShortDtos(new ArrayList<>(comp.getEventIds()));
                }
                dto.setEvents(currentEvents);
            }

            log.info("Подборка с ID: {} успешно обновлена.", id);
            return dto;
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("Ошибка при сохранении обновленной категории с ID: " + id);
        }
    }

    /**
     * Получить краткие DTO событий по списку ID напрямую через репозиторий
     */
    private List<EventShortDto> getEventShortDtos(List<Long> ids) {
        List<Event> events = eventRepository.findAllByIdIn(ids);

        List<Long> initiatorIds = events.stream()
                .map(Event::getInitiatorId)
                .distinct()
                .collect(Collectors.toList());

        Map<Long, UserDto> usersMap = new HashMap<>();
        try {
            List<UserDto> users = userClient.getUsers(initiatorIds);
            usersMap = users.stream().collect(Collectors.toMap(UserDto::getId, u -> u));
        } catch (Exception e) {
            log.warn("Failed to fetch users for compilations events", e);
        }

        final Map<Long, UserDto> finalUsersMap = usersMap;
        return events.stream().map(event -> {
            EventShortDto dto = eventMapper.toShortDto(event);
            UserDto user = finalUsersMap.get(event.getInitiatorId());
            if (user != null) {
                dto.setInitiator(UserShortDto.builder().id(user.getId()).name(user.getName()).build());
            }
            return dto;
        }).collect(Collectors.toList());
    }
}
