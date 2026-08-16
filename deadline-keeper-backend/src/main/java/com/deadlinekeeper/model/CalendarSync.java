package com.deadlinekeeper.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "calendar_sync")
@Getter
@Setter
@NoArgsConstructor
public class CalendarSync {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "google_access_token")
    private String googleAccessToken;

    @Column(name = "google_refresh_token")
    private String googleRefreshToken;

    @Column(name = "sync_token")
    private String syncToken;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
