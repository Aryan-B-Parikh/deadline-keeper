package com.deadlinekeeper.controller;

import com.deadlinekeeper.dto.NotificationResponse;
import com.deadlinekeeper.security.SecurityUtils;
import com.deadlinekeeper.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ResponseEntity<List<NotificationResponse>> listNotifications(
            @RequestParam(required = false, defaultValue = "false") boolean unreadOnly) {
        UUID userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(notificationService.getUserNotifications(userId, unreadOnly));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable UUID id) {
        UUID userId = SecurityUtils.getCurrentUserId();
        notificationService.markAsRead(userId, id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> unreadCount() {
        UUID userId = SecurityUtils.getCurrentUserId();
        long count = notificationService.getUnreadCount(userId);
        return ResponseEntity.ok(Map.of("count", count));
    }
}
