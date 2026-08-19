package com.deadlinekeeper.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "events")
@Getter
@Setter
@NoArgsConstructor
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String type;

    @Column(name = "due_at", nullable = false)
    private Instant dueAt;

    @Column(nullable = false)
    private String timezone = "UTC";

    @Column(nullable = false)
    private String source;

    @Column(name = "source_reference")
    private String sourceReference;

    @Column(name = "source_file_url")
    private String sourceFileUrl;

    @Column(name = "ai_confidence")
    private Float aiConfidence;

    @Column(name = "confirmation_status")
    private String confirmationStatus = "system";

    @Column(name = "user_confirmed")
    private Boolean userConfirmed = false;

    @Column(nullable = false)
    private String status = "upcoming";

    private String notes;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
