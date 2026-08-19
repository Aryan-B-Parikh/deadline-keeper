package com.deadlinekeeper.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
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

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "due_time")
    private LocalTime dueTime;

    @Column(nullable = false)
    private String timezone = "UTC";

    @Column(nullable = false)
    private String source;

    @Column(name = "source_reference")
    private String sourceReference;

    @Column(name = "source_file_url")
    private String sourceFileUrl;

    @Column(name = "confidence_score")
    private Float confidenceScore = 1.0f;

    @Column(nullable = false)
    private String status = "upcoming";

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "reminder_schedule", columnDefinition = "text[]")
    private List<String> reminderSchedule;

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
