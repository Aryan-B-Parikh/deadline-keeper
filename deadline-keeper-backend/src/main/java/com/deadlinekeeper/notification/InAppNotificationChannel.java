package com.deadlinekeeper.notification;

import com.deadlinekeeper.model.Notification;
import com.deadlinekeeper.model.User;
import com.deadlinekeeper.repository.NotificationRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
public class InAppNotificationChannel implements NotificationChannel {

    private final NotificationRepository notificationRepository;

    public InAppNotificationChannel(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Override
    public void send(User user, String title, String message, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new NotificationPermanentException("In-app notification requires an idempotency key");
        }

        if (notificationRepository.findByIdempotencyKey(idempotencyKey).isPresent()) return;

        Notification notification = new Notification();
        notification.setUserId(user.getId());
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setChannel("in_app");
        notification.setIdempotencyKey(idempotencyKey);

        try {
            notificationRepository.saveAndFlush(notification);
        } catch (DataIntegrityViolationException duplicate) {
            // Another worker created the same deterministic notification first.
        }
    }

    @Override
    public String getChannelName() {
        return "in_app";
    }
}
