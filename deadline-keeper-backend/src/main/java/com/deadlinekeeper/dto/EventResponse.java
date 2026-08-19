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
    @Deprecated
    private LocalDate dueDate; // legacy
    @Deprecated
    private LocalTime dueTime; // legacy
    private Instant dueAt;
    private String timezone;
    private String source;
    @Deprecated
    private Float confidenceScore; // legacy
    private Float aiConfidence;
    private String status;
    @Deprecated
    private List<String> reminderSchedule;
    private String notes;
    private String sourceFileUrl;
    private Instant createdAt;
    private Instant updatedAt;
}
