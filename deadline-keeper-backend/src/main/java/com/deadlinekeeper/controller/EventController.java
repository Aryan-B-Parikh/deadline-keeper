package com.deadlinekeeper.controller;

import com.deadlinekeeper.dto.*;
import com.deadlinekeeper.security.SecurityUtils;
import com.deadlinekeeper.service.EventService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @GetMapping
    public ResponseEntity<List<EventResponse>> listEvents(
            @RequestParam(required = false) String status) {
        UUID userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(eventService.getUserEvents(userId, status));
    }

    @GetMapping("/{id}")
    public ResponseEntity<EventResponse> getEvent(@PathVariable UUID id) {
        UUID userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(eventService.getEvent(userId, id));
    }

    @PostMapping
    public ResponseEntity<EventResponse> createEvent(@Valid @RequestBody EventRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        EventResponse event = eventService.createEvent(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(event);
    }

    @PutMapping("/{id}")
    public ResponseEntity<EventResponse> updateEvent(
            @PathVariable UUID id, @Valid @RequestBody EventRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(eventService.updateEvent(userId, id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEvent(@PathVariable UUID id) {
        UUID userId = SecurityUtils.getCurrentUserId();
        eventService.deleteEvent(userId, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/done")
    public ResponseEntity<EventResponse> markDone(@PathVariable UUID id) {
        UUID userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(eventService.markAsDone(userId, id));
    }

    @PostMapping("/{id}/snooze")
    public ResponseEntity<EventResponse> snooze(
            @PathVariable UUID id, @RequestBody SnoozeRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(eventService.snoozeEvent(userId, id, request.getDuration()));
    }
}
