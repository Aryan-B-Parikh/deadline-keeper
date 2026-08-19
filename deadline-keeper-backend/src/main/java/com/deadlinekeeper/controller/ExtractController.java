package com.deadlinekeeper.controller;

import com.deadlinekeeper.dto.EventResponse;
import com.deadlinekeeper.dto.ExtractConfirmRequest;
import com.deadlinekeeper.dto.ExtractionResult;
import com.deadlinekeeper.security.SecurityUtils;
import com.deadlinekeeper.service.ExtractionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

@RestController
@RequestMapping("/api/events/extract")
public class ExtractController {

    private final ExtractionService extractionService;

    private final Map<UUID, Queue<Instant>> extractionCounts = new ConcurrentHashMap<>();

    public ExtractController(ExtractionService extractionService) {
        this.extractionService = extractionService;
    }

    @PostMapping
    public ResponseEntity<?> extract(
            @RequestParam(required = false) MultipartFile screenshot,
            @RequestParam(required = false) String pastedText) {

        UUID userId = SecurityUtils.getCurrentUserId();
        if (!checkRateLimit(userId)) {
            return ResponseEntity.status(429).body(Map.of(
                    "error", Map.of("code", "RATE_LIMITED", "message", "Too many extraction requests. Try again later.")
            ));
        }

        if (screenshot != null && !screenshot.isEmpty()) {
            return ResponseEntity.ok(extractionService.extractFromImage(screenshot));
        } else if (pastedText != null && !pastedText.isBlank()) {
            return ResponseEntity.ok(extractionService.extractFromText(pastedText));
        }

        return ResponseEntity.badRequest().build();
    }

    @PostMapping("/confirm")
    public ResponseEntity<List<EventResponse>> confirm(@RequestBody ExtractConfirmRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        List<EventResponse> events = extractionService.confirmAndSave(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(events);
    }

    private boolean checkRateLimit(UUID userId) {
        Instant oneHourAgo = Instant.now().minusSeconds(3600);
        Queue<Instant> timestamps = extractionCounts.computeIfAbsent(userId, k -> new ConcurrentLinkedQueue<>());

        while (!timestamps.isEmpty() && timestamps.peek().isBefore(oneHourAgo)) {
            timestamps.poll();
        }

        if (timestamps.size() >= 10) return false;
        timestamps.add(Instant.now());
        return true;
    }
}
