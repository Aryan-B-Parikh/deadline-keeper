package com.deadlinekeeper.notification;

import com.deadlinekeeper.model.User;
import org.springframework.stereotype.Component;

@Component
public class InAppNotificationChannel implements NotificationChannel {

    @Override
    public void send(User user, String title, String message) {
        // In-app notifications are persisted in NotificationService.send()
        // This channel is a no-op since the DB write happens in the service layer
    }

    @Override
    public String getChannelName() {
        return "in_app";
    }
}
