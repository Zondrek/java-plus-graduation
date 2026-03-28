package ru.practicum.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.dto.comment.CommentDto;
import ru.practicum.dto.comment.NewCommentDto;
import ru.practicum.dto.user.UserDto;
import ru.practicum.enumeration.ParticipationStatus;
import ru.practicum.exception.ConflictException;
import ru.practicum.exception.NotFoundException;
import ru.practicum.feign.RequestClient;
import ru.practicum.feign.UserClient;
import ru.practicum.mapper.CommentMapper;
import ru.practicum.model.Comment;
import ru.practicum.model.Event;
import ru.practicum.repository.CommentRepository;
import ru.practicum.repository.EventRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static ru.practicum.enumeration.EventState.PUBLISHED;

@Slf4j
@RequiredArgsConstructor
@Service
@Transactional
public class CommentService {

    private final CommentRepository commRep;
    private final EventRepository eventRepository;
    private final RequestClient requestClient;
    private final UserClient userClient;
    private final CommentMapper mapper;

    /**
     * Создание комментария для события
     */
    public CommentDto addComment(Long userId, NewCommentDto newCommentDto) {
        Long eventId = newCommentDto.getEventId();
        log.info("Попытка создания нового комментария для события ID: {} от пользователя ID: {}", eventId, userId);

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Событие с ID: " + eventId + " не найдено."));

        // Статус события должен быть "Опубликовано"
        if (!PUBLISHED.equals(event.getState())) {
            throw new ConflictException("Статус события: " + event.getState() + " не соответствует ожидаемому.");
        }

        /*
         * Нужна ли пре-модерация заявок на участие
         * true - заявки должны одобряться инициатором
         * false - заявки принимаются автоматически
         */
        if (Boolean.TRUE.equals(event.getRequestModeration())) {
            if (!requestClient.participationExists(userId, eventId, ParticipationStatus.CONFIRMED)) {
                throw new ConflictException("Пользователь с ID: "
                        + userId + " не найден среди участников события с ID: "
                        + eventId);
            }
        }

        // Проверяем существование пользователя
        UserDto user = userClient.getUser(userId);
        if (user == null || user.getId() == null) {
            throw new NotFoundException("Пользователь с ID: " + userId + " не найден.");
        }

        try {
            Comment comment = mapper.toEntity(newCommentDto);
            comment.setCreatedOn(LocalDateTime.now());
            comment.setAuthorId(userId);
            comment.setEventId(eventId);

            Comment savedComment = commRep.save(comment);
            CommentDto commentDto = mapper.toDto(savedComment);
            commentDto.setAuthorName(user.getName());

            log.info("Успешное сохранение нового комментария ID: {},"
                    + " для события ID: {},"
                    + " от пользователя ID: {}", savedComment.getId(), eventId, userId);

            return commentDto;
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("Непредвиденная ошибка при сохранении нового комментария.");
        }
    }

    /**
     * Поиск всех комментариев для определенного события
     */
    @Transactional(readOnly = true)
    public List<CommentDto> findAllByIdEvent(Long eventId) {
        log.info("Попытка получения коллекции комментариев");
        if (eventId == null) {
            log.info("Возврат пустого списка комментариев.");
            return new ArrayList<>();
        }

        // Проверяем существование события напрямую через репозиторий
        if (!eventRepository.existsById(eventId)) {
            throw new NotFoundException("Событие с ID: " + eventId + " не найдено.");
        }

        List<Comment> comments = commRep.findByEventId(eventId);

        if (comments.isEmpty()) {
            return new ArrayList<>();
        }

        // Собираем все authorId и батч-загружаем пользователей
        List<Long> authorIds = comments.stream()
                .map(Comment::getAuthorId)
                .distinct()
                .collect(Collectors.toList());

        List<UserDto> users = userClient.getUsers(authorIds);
        Map<Long, String> authorNameMap = users.stream()
                .collect(Collectors.toMap(UserDto::getId, UserDto::getName));

        List<CommentDto> result = comments.stream()
                .map(comment -> {
                    CommentDto dto = mapper.toDto(comment);
                    dto.setAuthorName(authorNameMap.getOrDefault(comment.getAuthorId(), "Unknown"));
                    return dto;
                })
                .collect(Collectors.toList());

        log.info("Список комментариев для события ID: {} успешно сформирован.", eventId);
        return result;
    }

    /**
     * Удаление комментария
     */
    public void deleteComment(Long userId, Long commentId) {
        Comment comment = commRep.findById(commentId).orElseThrow(
                () -> new NotFoundException("Комментарий с ID: " + commentId + " не найден."));

        // Проверяем существование пользователя
        UserDto user = userClient.getUser(userId);
        if (user == null || user.getId() == null) {
            throw new ConflictException("Пользователя с ID: " + userId + " не существует.");
        }

        // Проверка прав пользователя
        if (!comment.getAuthorId().equals(userId)) {
            throw new ConflictException("Вы не являетесь автором комментария с ID: " + commentId
                    + " и потому не можете удалить его.");
        }

        try {
            commRep.delete(comment);
            log.info("Успешное удаление комментария с ID: {}", commentId);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("Непредвиденная ошибка при удалении комментария с ID: " + commentId);
        }
    }
}
