package com.deadlinekeeper.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
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
        private Instant dueAt;
        private String timezone;
        private Float aiConfidence;
        private boolean needsClarification;
    }
}
