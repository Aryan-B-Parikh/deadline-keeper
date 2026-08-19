package com.deadlinekeeper.repository;

import com.deadlinekeeper.model.NotificationOutbox;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, UUID> {

    // Atomic claim: atomically transition one pending row to processing.
    // FOR UPDATE SKIP LOCKED ensures no two workers claim the same row.
    @Modifying
    @Query(value = """
            UPDATE notification_outbox
            SET status = 'processing',
                attempt_count = attempt_count + 1
            WHERE id IN (
                SELECT id FROM notification_outbox
                WHERE status = 'pending'
                  AND (next_retry_at IS NULL OR next_retry_at <= NOW())
                ORDER BY scheduled_at
                LIMIT :limit
                FOR UPDATE SKIP LOCKED
            )
            """, nativeQuery = true)
    int claimPendingJobs(@Param("limit") int limit);

    // After claiming, fetch the now-processing rows
    List<NotificationOutbox> findByStatusOrderByScheduledAtAsc(String status, Pageable pageable);

    Optional<NotificationOutbox> findByIdempotencyKey(String idempotencyKey);

    long countByStatus(String status);
}
