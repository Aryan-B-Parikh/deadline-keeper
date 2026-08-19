package com.deadlinekeeper.repository;

import com.deadlinekeeper.model.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EventRepository extends JpaRepository<Event, UUID> {

    List<Event> findByUserIdAndStatus(UUID userId, String status);

    List<Event> findByUserId(UUID userId);

    Optional<Event> findByIdAndUserId(UUID id, UUID userId);

    @Query("SELECT e FROM Event e WHERE e.userId = :userId AND e.status IN :statuses ORDER BY e.dueAt ASC")
    List<Event> findByUserIdAndStatusIn(@Param("userId") UUID userId, @Param("statuses") List<String> statuses);

    @Query("SELECT e FROM Event e WHERE e.status != 'done' AND e.dueAt <= :deadline")
    List<Event> findPendingBefore(@Param("deadline") Instant deadline);

    @Query("SELECT e FROM Event e WHERE e.status IN ('upcoming','due_soon','overdue') AND e.dueAt BETWEEN :from AND :to")
    List<Event> findActiveBetween(@Param("from") Instant from, @Param("to") Instant to);


    @Query("SELECT e FROM Event e WHERE e.status IN ('upcoming', 'due_soon')")
    List<Event> findAllActiveEvents();

    @Query("SELECT e FROM Event e WHERE e.userId = :userId AND e.source = :source AND e.sourceReference = :sourceRef")
    List<Event> findByUserIdAndSourceAndSourceReference(
            @Param("userId") UUID userId,
            @Param("source") String source,
            @Param("sourceRef") String sourceRef);
}
