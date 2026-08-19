package com.deadlinekeeper.repository;

import com.deadlinekeeper.model.NotificationOutbox;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, UUID> {

    List<NotificationOutbox> findByStatusInOrderByScheduledAtAsc(List<String> statuses, Pageable pageable);

    Optional<NotificationOutbox> findByIdempotencyKey(String idempotencyKey);

    long countByStatus(String status);

    @Query("SELECT n FROM NotificationOutbox n WHERE n.eventId = :eventId AND n.channel = :channel AND n.createdAt >= :since")
    List<NotificationOutbox> findRecentByEventAndChannel(@Param("eventId") UUID eventId, @Param("channel") String channel, @Param("since") Instant since);
}
