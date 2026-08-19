package com.deadlinekeeper.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification_outbox")
@Getter
@Setter
@NoArgsConstructor
public class NotificationOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "delivery_id")
    private UUID deliveryId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String message;

    @Column(nullable = false)
    private String channel;

    @Column(nullable = false)
    private String status = "pending";

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(name = "attempt_count")
    private Integer attemptCount = 0;

    @Column(name = "max_attempts")
    private Integer maxAttempts = 3;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    /** When the worker started processing this entry (set on claim). */
    @Column(name = "processing_started_at")
    private Instant processingStartedAt;

    /** Lease expires at. Worker must complete before this time or the watchdog reclaims. */
    @Column(name = "lease_until")
    private Instant leaseUntil;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        if (scheduledAt == null) {
            scheduledAt = Instant.now();
        }
    }
}
