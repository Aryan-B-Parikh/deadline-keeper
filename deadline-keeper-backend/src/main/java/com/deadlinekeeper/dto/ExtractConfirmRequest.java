package com.deadlinekeeper.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Getter
@Setter
public class ExtractConfirmRequest {

    private List<ConfirmedEvent> events;
    private String sourceType;
    private String sourceReference;
    private String sourceFileUrl;

    @Getter
    @Setter
    public static class ConfirmedEvent {
        private String title;
        private String type;
        private Instant dueAt;
        @Deprecated
        private LocalDate dueDate; // legacy
        @Deprecated
        private LocalTime dueTime; // legacy
        private String timezone;

        private List<ReminderRequest> reminders;
        private String notes;
    }
}
