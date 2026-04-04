package ru.practicum.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.practicum.model.UserAction;
import ru.practicum.model.UserActionId;

import java.util.List;

public interface UserActionRepository extends JpaRepository<UserAction, UserActionId> {

    @Query("SELECT ua FROM UserAction ua WHERE ua.id.userId = :userId ORDER BY ua.timestamp DESC")
    List<UserAction> findByUserIdOrderByTimestampDesc(@Param("userId") Long userId);

    @Query("SELECT ua FROM UserAction ua WHERE ua.id.userId = :userId AND ua.id.eventId IN :eventIds")
    List<UserAction> findByUserIdAndEventIds(@Param("userId") Long userId,
                                             @Param("eventIds") List<Long> eventIds);

    @Query("SELECT SUM(ua.maxWeight) FROM UserAction ua WHERE ua.id.eventId = :eventId")
    Double sumWeightsByEventId(@Param("eventId") Long eventId);
}
