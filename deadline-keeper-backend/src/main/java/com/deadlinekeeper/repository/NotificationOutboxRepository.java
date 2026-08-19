package com.deadlinekeeper.repository;

import com.deadlinekeeper.model.NotificationOutbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, UUID> {

    /**
     * Atomically claim up to {@code limit} pending jobs by transitioning them to 'processing'.
     * FOR UPDATE SKIP LOCKED prevents two workers from claiming the same row.
     * Sets processing_started_at and lease_until (lease = now + leaseSeconds).
     *
     * @return number of rows claimed
     */
    @Modifying
    @Query(value = """
            UPDATE notification_outbox
            SET status = 'processing',
                attempt_count = attempt_count + 1,
                processing_started_at = NOW(),
                lease_until = NOW() + (:leaseSeconds || ' seconds')::interval
            WHERE id IN (
                SELECT id FROM notification_outbox
                WHERE status = 'pending'
                  AND (next_retry_at IS NULL OR next_retry_at <= NOW())
                ORDER BY scheduled_at
                LIMIT :limit
                FOR UPDATE SKIP LOCKED
            )
            """, nativeQuery = true)
    int claimPendingJobs(@Param("limit") int limit, @Param("leaseSeconds") long leaseSeconds);

    /**
     * Reclaim expired processing rows: reset to 'pending' if lease has expired.
     * Used by the watchdog to recover from worker crashes.
     *
     * @return number of rows reclaimed
     */
    @Modifying
    @Query(value = """
            UPDATE notification_outbox
            SET status = 'pending',
                processing_started_at = NULL,
                lease_until = NULL,
                last_error = 'Lease expired (worker crash)'
            WHERE status = 'processing'
              AND lease_until < NOW()
            """, nativeQuery = true)
    int reclaimExpiredLeases();

    /**
     * Fetch all rows in 'processing' state (called after claim to get the full entities).
     */
    List<NotificationOutbox> findByStatusOrderByScheduledAtAsc(String status);

    Optional<NotificationOutbox> findByIdempotencyKey(String idempotencyKey);

    long countByStatus(String status);
}
