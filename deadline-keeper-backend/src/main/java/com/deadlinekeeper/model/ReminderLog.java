package com.deadlinekeeper.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reminder_logs")
@Getter
@Setter
@NoArgsConstructor
public class ReminderLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "offset_fired", nullable = false)
    private String offsetFired;

    @Column(name = "fired_at", updatable = false)
    private Instant firedAt;

    @PrePersist
    protected void onCreate() {
        firedAt = Instant.now();
    }
}
