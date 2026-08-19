package com.deadlinekeeper.notification;

import com.deadlinekeeper.model.User;

import java.util.UUID;

public interface NotificationChannel {

    /** Send a notification through this channel. */
    void send(User user, String title, String message, String idempotencyKey, UUID eventId);

    String getChannelName();
}
