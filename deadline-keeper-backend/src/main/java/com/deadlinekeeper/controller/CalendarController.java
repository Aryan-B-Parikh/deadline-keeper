package com.deadlinekeeper.controller;

import com.deadlinekeeper.security.SecurityUtils;
import com.deadlinekeeper.service.CalendarSyncService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/calendar/sync")
public class CalendarController {

    private final CalendarSyncService calendarSyncService;

    public CalendarController(CalendarSyncService calendarSyncService) {
        this.calendarSyncService = calendarSyncService;
    }

    @GetMapping("/start")
    public ResponseEntity<Void> startOAuth() {
        String authUrl = calendarSyncService.getAuthorizationUrl();
        return ResponseEntity.status(302).location(URI.create(authUrl)).build();
    }

    @GetMapping("/callback")
    public ResponseEntity<Map<String, String>> callback(@RequestParam("code") String code) {
        UUID userId = SecurityUtils.getCurrentUserId();
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
