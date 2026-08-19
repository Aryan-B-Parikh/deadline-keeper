package com.deadlinekeeper.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "calendar_connections")
@Getter
@Setter
@NoArgsConstructor
public class CalendarConnection {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(nullable = false)
    private String provider = "google";

    @Column(name = "encrypted_access_token")
    private String encryptedAccessToken;

    @Column(name = "encrypted_refresh_token")
    private String encryptedRefreshToken;

    @Column(name = "token_iv")
    private String tokenIv;

    @Column(name = "sync_token")
    private String syncToken;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

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
