package com.deadlinekeeper.mapper;

import com.deadlinekeeper.dto.EventRequest;
import com.deadlinekeeper.dto.EventResponse;
import com.deadlinekeeper.exception.ValidationException;
import com.deadlinekeeper.model.Event;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

@Component
public class EventCompatibilityMapper {

    public Instant resolveCanonicalDueAt(EventRequest request) {
        if (request.getDueAt() != null && request.getDueDate() != null) {
            // Priority: dueAt wins, but we should reject if they conflict or just document it.
            // For now, dueAt explicitly wins.
        }

        if (request.getDueAt() != null) {
            return request.getDueAt();
        }

        if (request.getDueDate() == null) {
            throw new ValidationException("Either dueAt or legacy dueDate must be provided");
        }

        String tz = request.getTimezone() != null ? request.getTimezone() : "UTC";
        ZoneId zone;
        try {
            zone = ZoneId.of(tz);
        } catch (Exception e) {
            zone = ZoneOffset.UTC;
        }

        LocalTime time = request.getDueTime() != null ? request.getDueTime() : LocalTime.of(23, 59);
        return request.getDueDate().atTime(time).atZone(zone).toInstant();
    }

    public EventResponse toResponse(Event event) {
        ZoneId zone;
        try {
            zone = ZoneId.of(event.getTimezone());
        } catch (Exception e) {
            zone = ZoneOffset.UTC;
        }

        LocalDate dueDate = null;
        LocalTime dueTime = null;
        if (event.getDueAt() != null) {
            var zoned = event.getDueAt().atZone(zone);
            dueDate = zoned.toLocalDate();
            dueTime = zoned.toLocalTime();
        }

        return EventResponse.builder()
                .id(event.getId())
                .title(event.getTitle())
                .type(event.getType())
                .dueAt(event.getDueAt())
                .dueDate(dueDate)
                .dueTime(dueTime)
                .timezone(event.getTimezone())
                .source(event.getSource())
                .confidenceScore(event.getAiConfidence() != null ? event.getAiConfidence() : 1.0f)
                .aiConfidence(event.getAiConfidence() != null ? event.getAiConfidence() : 1.0f)
                .status(event.getStatus())
                .notes(event.getNotes())
                .sourceFileUrl(event.getSourceFileUrl())
                .createdAt(event.getCreatedAt())
                .updatedAt(event.getUpdatedAt())
                .build();
    }
}
