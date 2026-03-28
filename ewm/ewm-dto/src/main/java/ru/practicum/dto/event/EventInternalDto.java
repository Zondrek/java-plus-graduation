package ru.practicum.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.practicum.enumeration.EventState;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EventInternalDto {
    private Long id;
    private Long initiatorId;
    private Long categoryId;
    private EventState state;
    private Integer participantLimit;
    private Integer confirmedRequests;
    private Boolean requestModeration;
    private LocalDateTime publishedOn;
    private String title;
}
