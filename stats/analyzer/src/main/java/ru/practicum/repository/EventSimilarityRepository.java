package ru.practicum.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.practicum.model.EventSimilarity;
import ru.practicum.model.EventSimilarityId;

import java.util.List;

public interface EventSimilarityRepository extends JpaRepository<EventSimilarity, EventSimilarityId> {

    @Query("SELECT es FROM EventSimilarity es WHERE es.id.eventA = :eventId OR es.id.eventB = :eventId "
            + "ORDER BY es.score DESC")
    List<EventSimilarity> findByEventId(@Param("eventId") Long eventId);
}
