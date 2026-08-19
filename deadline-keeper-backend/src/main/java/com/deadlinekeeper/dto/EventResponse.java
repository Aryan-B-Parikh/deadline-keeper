package com.deadlinekeeper.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class EventResponse {
    private UUID id;
    private String title;
    private String type;
    private Instant dueAt;
    private String timezone;
    private String source;
    private Float aiConfidence;
    private String status;

    private List<ReminderResponse> reminders;
    private String notes;
    private String sourceFileUrl;
    private Instant createdAt;
    private Instant updatedAt;
}
