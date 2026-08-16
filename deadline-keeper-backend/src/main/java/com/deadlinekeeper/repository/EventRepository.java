package com.deadlinekeeper.repository;

import com.deadlinekeeper.model.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface EventRepository extends JpaRepository<Event, UUID> {

    List<Event> findByUserIdAndStatus(UUID userId, String status);

    List<Event> findByUserId(UUID userId);

    @Query("SELECT e FROM Event e WHERE e.userId = :userId AND e.status IN :statuses ORDER BY e.dueDate ASC")
    List<Event> findByUserIdAndStatusIn(@Param("userId") UUID userId, @Param("statuses") List<String> statuses);

    @Query("SELECT e FROM Event e WHERE e.status != 'done' AND e.dueDate <= :date")
    List<Event> findUpcomingEventsBefore(@Param("date") LocalDate date);

    @Query("SELECT e FROM Event e WHERE e.status IN ('upcoming', 'due_soon')")
    List<Event> findAllActiveEvents();

    @Query("SELECT e FROM Event e WHERE e.userId = :userId AND e.source = :source AND e.sourceReference = :sourceRef")
    List<Event> findByUserIdAndSourceAndSourceReference(
            @Param("userId") UUID userId,
            @Param("source") String source,
            @Param("sourceRef") String sourceRef);
}
