package com.deadlinekeeper.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
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

        private String timezone;

        private List<ReminderRequest> reminders;
        private String notes;
    }
}
