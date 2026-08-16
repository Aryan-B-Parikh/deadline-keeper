package com.deadlinekeeper.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class NotificationResponse {
    private UUID id;
    private UUID eventId;
    private String title;
    private String message;
    private Boolean isRead;
    private String channel;
    private Instant createdAt;
}
