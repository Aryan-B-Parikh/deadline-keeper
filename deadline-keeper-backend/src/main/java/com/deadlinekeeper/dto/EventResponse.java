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
    private LocalDate dueDate;
    private LocalTime dueTime;
    private String timezone;
    private String source;
    private Float confidenceScore;
    private String status;
    private List<String> reminderSchedule;
    private String notes;
    private String sourceFileUrl;
    private Instant createdAt;
    private Instant updatedAt;
}
