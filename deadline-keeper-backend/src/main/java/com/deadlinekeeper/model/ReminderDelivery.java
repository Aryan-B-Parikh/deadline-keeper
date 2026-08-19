package com.deadlinekeeper.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reminder_deliveries")
@Getter
@Setter
@NoArgsConstructor
public class ReminderDelivery {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "reminder_id", nullable = false)
    private UUID reminderId;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(nullable = false)
    private String status = "pending";

    @Column(name = "last_error")
    private String lastError;

    @Column(nullable = false)
    private String channel;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
