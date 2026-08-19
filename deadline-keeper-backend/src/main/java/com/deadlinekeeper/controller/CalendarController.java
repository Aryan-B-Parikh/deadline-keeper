package com.deadlinekeeper.controller;

import com.deadlinekeeper.model.CalendarConnection;
import com.deadlinekeeper.repository.CalendarConnectionRepository;
import com.deadlinekeeper.security.SecurityUtils;
import com.deadlinekeeper.service.CalendarSyncService;
import jakarta.validation.ValidationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/calendar/sync")
public class CalendarController {

    private final CalendarSyncService calendarSyncService;
    private final CalendarConnectionRepository connectionRepository;

    public CalendarController(CalendarSyncService calendarSyncService,
                               CalendarConnectionRepository connectionRepository) {
        this.calendarSyncService = calendarSyncService;
        this.connectionRepository = connectionRepository;
    }

    @GetMapping("/start")
    public ResponseEntity<Void> startOAuth() {
        UUID userId = SecurityUtils.getCurrentUserId();
        String state = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plusSeconds(600);

        CalendarConnection conn = connectionRepository.findByUserId(userId)
                .orElse(new CalendarConnection());
        conn.setUserId(userId);
        conn.setOauthState(state);
        conn.setOauthStateExpiresAt(expiresAt);
        connectionRepository.save(conn);

        String authUrl = calendarSyncService.getAuthorizationUrl(state);
        return ResponseEntity.status(302).location(URI.create(authUrl)).build();
    }

    @GetMapping("/callback")
    public ResponseEntity<Map<String, String>> callback(
            @RequestParam("code") String code,
            @RequestParam(value = "state", required = false) String state) {

        if (state == null || state.isBlank()) {
            return ResponseEntity.status(400).body(Map.of("error", "Missing OAuth state parameter"));
        }

        // Transaction 1: Atomic single-use state consumption commits independently
        UUID userId = calendarSyncService.consumeStateAndGetUserId(state);
        if (userId == null) {
            return ResponseEntity.status(400).body(Map.of("error", "Invalid, expired, or already consumed OAuth state"));
        }

        // External provider call + Transaction 2: exchange code and persist tokens
        calendarSyncService.handleCallback(userId, code);
        return ResponseEntity.ok(Map.of("status", "connected"));
    }

    @PostMapping("/trigger")
    public ResponseEntity<Map<String, String>> triggerSync() {
        UUID userId = SecurityUtils.getCurrentUserId();
        calendarSyncService.syncEvents(userId);
        return ResponseEntity.ok(Map.of("status", "synced"));
    }

    @DeleteMapping
    public ResponseEntity<Void> disconnect() {
        UUID userId = SecurityUtils.getCurrentUserId();
        calendarSyncService.disconnect(userId);
        return ResponseEntity.noContent().build();
    }
}
