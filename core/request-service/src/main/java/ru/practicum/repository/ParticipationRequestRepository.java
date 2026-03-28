package ru.practicum.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.enumeration.ParticipationStatus;
import ru.practicum.model.ParticipationRequest;

import java.util.List;
import java.util.Optional;

@Repository
public interface ParticipationRequestRepository extends JpaRepository<ParticipationRequest, Long> {

    Optional<ParticipationRequest> findByRequesterIdAndEventId(Long requesterId, Long eventId);

    Boolean existsByRequesterIdAndEventIdAndStatus(Long requesterId, Long eventId, ParticipationStatus status);

    List<ParticipationRequest> findAllByRequesterId(Long requesterId);

    List<ParticipationRequest> findAllByEventId(Long eventId);

    List<ParticipationRequest> findAllByEventIdAndIdIn(Long eventId, List<Long> ids);

    List<ParticipationRequest> findAllByEventIdAndStatus(Long eventId, ParticipationStatus status);

    long countByEventIdAndStatus(Long eventId, ParticipationStatus status);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE ParticipationRequest r SET r.status = :status " +
            "WHERE r.eventId = :eventId AND r.id IN :ids AND r.status = 'PENDING'")
    void bulkUpdateStatus(@Param("eventId") Long eventId,
                          @Param("ids") List<Long> ids,
                          @Param("status") ParticipationStatus status);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE ParticipationRequest r SET r.status = :status " +
            "WHERE r.eventId = :eventId AND r.status = 'PENDING'")
    void rejectAllPendingRequests(@Param("eventId") Long eventId,
                                  @Param("status") ParticipationStatus status);
}
