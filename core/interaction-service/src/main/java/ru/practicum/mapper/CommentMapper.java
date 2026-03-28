package ru.practicum.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import ru.practicum.dto.comment.CommentDto;
import ru.practicum.dto.comment.NewCommentDto;
import ru.practicum.model.Comment;

@Mapper(componentModel = "spring")
public interface CommentMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdOn", ignore = true)
    @Mapping(target = "authorId", ignore = true)
    @Mapping(target = "eventId", source = "eventId")
    Comment toEntity(NewCommentDto dto);

    @Mapping(target = "authorId", source = "authorId")
    @Mapping(target = "authorName", ignore = true)
    CommentDto toDto(Comment comment);
}
