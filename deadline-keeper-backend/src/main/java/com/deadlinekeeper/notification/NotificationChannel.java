package com.deadlinekeeper.notification;

import com.deadlinekeeper.model.User;

public interface NotificationChannel {
    void send(User user, String title, String message);
    String getChannelName();
}
