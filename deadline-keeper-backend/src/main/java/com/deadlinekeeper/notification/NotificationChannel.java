package com.deadlinekeeper.notification;

import com.deadlinekeeper.model.User;

public interface NotificationChannel {

    /**
     * Send a notification to the user via this channel.
     *
     * @param user          the recipient
     * @param title         notification title
     * @param message       notification body
     * @param idempotencyKey optional provider-side idempotency key (may be null)
     */
    void send(User user, String title, String message, String idempotencyKey);

    String getChannelName();
}
