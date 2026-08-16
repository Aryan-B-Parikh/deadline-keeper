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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/events/extract")
public class ExtractController {

    private final ExtractionService extractionService;

    public ExtractController(ExtractionService extractionService) {
        this.extractionService = extractionService;
    }

    @PostMapping
    public ResponseEntity<ExtractionResult> extract(
            @RequestParam(required = false) MultipartFile screenshot,
            @RequestParam(required = false) String pastedText) {

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
}
