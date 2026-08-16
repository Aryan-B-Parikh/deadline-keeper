package com.deadlinekeeper.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Getter
@Builder
public class ExtractionResult {
    private List<ExtractedEvent> events;
    private boolean needsConfirmation;
    private String clarificationQuestion;

    @Getter
    @Builder
    public static class ExtractedEvent {
        private String title;
        private String type;
        private LocalDate dueDate;
        private LocalTime dueTime;
        private String timezone;
        private float confidenceScore;
        private boolean needsClarification;
    }
}
