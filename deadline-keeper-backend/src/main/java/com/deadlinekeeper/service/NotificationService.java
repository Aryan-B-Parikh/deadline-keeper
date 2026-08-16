package com.deadlinekeeper.service;

import com.deadlinekeeper.dto.NotificationResponse;
import com.deadlinekeeper.model.Notification;
import com.deadlinekeeper.model.User;
import com.deadlinekeeper.notification.NotificationChannel;
import com.deadlinekeeper.repository.NotificationRepository;
import com.deadlinekeeper.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final List<NotificationChannel> channels;

    public NotificationService(NotificationRepository notificationRepository,
                               UserRepository userRepository,
                               List<NotificationChannel> channels) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.channels = channels;
    }

    public void send(User user, String title, String message, UUID eventId) {
        for (NotificationChannel channel : channels) {
            try {
                channel.send(user, title, message);

                Notification notification = new Notification();
                notification.setUserId(user.getId());
                notification.setEventId(eventId);
                notification.setTitle(title);
                notification.setMessage(message);
                notification.setChannel(channel.getChannelName());
                notificationRepository.save(notification);
            } catch (Exception e) {
                // Log but don't fail other channels
                System.err.println("Failed to send notification via " + channel.getChannelName() + ": " + e.getMessage());
            }
        }
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
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new RuntimeException("Notification not found"));
        if (!notification.getUserId().equals(userId)) {
            throw new RuntimeException("Access denied");
        }
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
