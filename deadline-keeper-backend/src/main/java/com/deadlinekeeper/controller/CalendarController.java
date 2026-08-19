package com.deadlinekeeper.controller;

import com.deadlinekeeper.security.SecurityUtils;
import com.deadlinekeeper.service.CalendarSyncService;
import jakarta.validation.ValidationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/calendar/sync")
public class CalendarController {

    private final CalendarSyncService calendarSyncService;

    private final Map<String, StateEntry> stateCache = new ConcurrentHashMap<>();

    public CalendarController(CalendarSyncService calendarSyncService) {
        this.calendarSyncService = calendarSyncService;
    }

    @GetMapping("/start")
    public ResponseEntity<Void> startOAuth() {
        UUID userId = SecurityUtils.getCurrentUserId();
        String state = UUID.randomUUID().toString();
        stateCache.put(state, new StateEntry(userId, Instant.now().plusSeconds(600)));

        String authUrl = calendarSyncService.getAuthorizationUrl(state);
        return ResponseEntity.status(302).location(URI.create(authUrl)).build();
    }

    @GetMapping("/callback")
    public ResponseEntity<Map<String, String>> callback(
            @RequestParam("code") String code,
            @RequestParam(value = "state", required = false) String state) {

        if (state == null || !stateCache.containsKey(state)) {
            throw new ValidationException("Invalid or expired OAuth state");
        }

        StateEntry entry = stateCache.remove(state);
        if (Instant.now().isAfter(entry.expiresAt())) {
            throw new ValidationException("OAuth state expired");
        }

        calendarSyncService.handleCallback(entry.userId, code);
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

    private record StateEntry(UUID userId, Instant expiresAt) {}
}
