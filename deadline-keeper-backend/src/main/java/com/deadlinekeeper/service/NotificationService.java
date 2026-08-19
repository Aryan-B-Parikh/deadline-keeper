package com.deadlinekeeper.service;

import com.deadlinekeeper.dto.NotificationResponse;
import com.deadlinekeeper.exception.ResourceNotFoundException;
import com.deadlinekeeper.model.Notification;
import com.deadlinekeeper.model.NotificationOutbox;
import com.deadlinekeeper.model.User;
import com.deadlinekeeper.repository.NotificationOutboxRepository;
import com.deadlinekeeper.repository.NotificationRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationOutboxRepository outboxRepository;

    public NotificationService(NotificationRepository notificationRepository,
                               NotificationOutboxRepository outboxRepository) {
        this.notificationRepository = notificationRepository;
        this.outboxRepository = outboxRepository;
    }

    public void send(User user, String title, String message, UUID eventId) {
        String idempotencyKey = "direct_%s_%s".formatted(
                UUID.randomUUID(), Instant.now().toEpochMilli());

        NotificationOutbox outbox = new NotificationOutbox();
        outbox.setUserId(user.getId());
        outbox.setEventId(eventId);
        outbox.setTitle(title);
        outbox.setMessage(message);
        outbox.setChannel("in_app");
        outbox.setIdempotencyKey(idempotencyKey);
        outbox.setStatus("pending");
        outbox.setAttemptCount(0);
        outbox.setMaxAttempts(1);
        outbox.setScheduledAt(Instant.now());
        outboxRepository.save(outbox);
    }

    public List<NotificationResponse> getUserNotifications(UUID userId, boolean unreadOnly) {
        List<Notification> notifications;
        if (unreadOnly) {
            notifications = notificationRepository.findByUserIdAndIsReadFalseOrderByCreatedAtDesc(userId);
        } else {
            notifications = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);
        }
        return notifications.stream().map(this::toResponse).toList();
    }

    public void markAsRead(UUID userId, UUID notificationId) {
        Notification notification = notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", notificationId.toString()));
        notification.setIsRead(true);
        notificationRepository.save(notification);
    }

    public long getUnreadCount(UUID userId) {
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    private NotificationResponse toResponse(Notification notification) {
        return NotificationResponse.builder()
                .id(notification.getId())
                .eventId(notification.getEventId())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .isRead(notification.getIsRead())
                .channel(notification.getChannel())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}
