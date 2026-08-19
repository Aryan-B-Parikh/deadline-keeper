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
     * RETURNING id returns only the exact rows claimed by this worker.
     */
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
            RETURNING id
            """, nativeQuery = true)
    List<UUID> claimPendingJobIds(@Param("limit") int limit, @Param("leaseSeconds") long leaseSeconds);

    /**
     * Conditional update: Mark job as 'sent' only if the worker still holds a valid lease on this processing job.
     */
    @Modifying
    @Query(value = """
            UPDATE notification_outbox
            SET status = 'sent',
                lease_until = NULL
            WHERE id = :id
              AND status = 'processing'
              AND lease_until >= NOW()
            """, nativeQuery = true)
    int markSentIfOwned(@Param("id") UUID id);

    /**
     * Conditional update: Re-queue job as 'pending' with backoff only if the worker still holds a valid lease.
     */
    @Modifying
    @Query(value = """
            UPDATE notification_outbox
            SET status = 'pending',
                last_error = :error,
                lease_until = NULL,
                processing_started_at = NULL,
                next_retry_at = :nextRetryAt
            WHERE id = :id
              AND status = 'processing'
              AND lease_until >= NOW()
            """, nativeQuery = true)
    int markRetryIfOwned(@Param("id") UUID id,
                         @Param("nextRetryAt") Instant nextRetryAt,
                         @Param("error") String error);

    /**
     * Conditional update: Mark job as 'failed' permanently only if the worker still holds a valid lease.
     */
    @Modifying
    @Query(value = """
            UPDATE notification_outbox
            SET status = 'failed',
                last_error = :error,
                lease_until = NULL
            WHERE id = :id
              AND status = 'processing'
              AND lease_until >= NOW()
            """, nativeQuery = true)
    int markFailedIfOwned(@Param("id") UUID id,
                          @Param("error") String error);

    /**
     * Find delivery IDs for expired processing rows whose attempt_count >= max_attempts.
     */
    @Query(value = """
            SELECT delivery_id FROM notification_outbox
            WHERE status = 'processing'
              AND lease_until < NOW()
              AND attempt_count >= max_attempts
              AND delivery_id IS NOT NULL
            """, nativeQuery = true)
    List<UUID> findExpiredDeliveryIdsExceedingMaxAttempts();

    /**
     * Reclaim expired processing rows whose attempt_count >= max_attempts by transitioning to 'failed'.
     */
    @Modifying
    @Query(value = """
            UPDATE notification_outbox
            SET status = 'failed',
                last_error = 'Lease expired (worker crash) - max attempts exceeded',
                lease_until = NULL,
                processing_started_at = NULL
            WHERE status = 'processing'
              AND lease_until < NOW()
              AND attempt_count >= max_attempts
            """, nativeQuery = true)
    int failExpiredLeasesExceedingMaxAttempts();

    /**
     * Reclaim expired processing rows with remaining attempts by resetting to 'pending'
     * with exponential backoff calculated per row from its attempt_count.
     */
    @Modifying
    @Query(value = """
            UPDATE notification_outbox
            SET status = 'pending',
                processing_started_at = NULL,
                lease_until = NULL,
                last_error = 'Lease expired (worker crash)',
                next_retry_at = NOW() + (
                    LEAST(:maxBackoffSeconds, :baseBackoffSeconds * POWER(2, GREATEST(0, attempt_count - 1))) || ' seconds'
                )::interval
            WHERE status = 'processing'
              AND lease_until < NOW()
              AND attempt_count < max_attempts
            """, nativeQuery = true)
    int reclaimExpiredLeasesWithExponentialBackoff(
            @Param("baseBackoffSeconds") long baseBackoffSeconds,
            @Param("maxBackoffSeconds") long maxBackoffSeconds);

    List<NotificationOutbox> findByStatusOrderByScheduledAtAsc(String status);

    Optional<NotificationOutbox> findByIdempotencyKey(String idempotencyKey);

    long countByStatus(String status);
}
