package ru.practicum.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EventSimilarityId implements Serializable {

    @Column(name = "event_a")
    private Long eventA;

    @Column(name = "event_b")
    private Long eventB;
}
