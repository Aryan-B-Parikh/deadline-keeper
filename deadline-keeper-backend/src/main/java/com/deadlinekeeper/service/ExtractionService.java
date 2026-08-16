package com.deadlinekeeper.service;

import com.deadlinekeeper.dto.ExtractConfirmRequest;
import com.deadlinekeeper.dto.EventResponse;
import com.deadlinekeeper.dto.ExtractionResult;
import com.deadlinekeeper.integration.GeminiClient;
import com.deadlinekeeper.model.Event;
import com.deadlinekeeper.repository.EventRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ExtractionService {

    private final GeminiClient geminiClient;
    private final EventRepository eventRepository;

    public ExtractionService(GeminiClient geminiClient, EventRepository eventRepository) {
        this.geminiClient = geminiClient;
        this.eventRepository = eventRepository;
    }

    public ExtractionResult extractFromText(String text) {
        JsonNode result = geminiClient.extractFromText(text);
        return parseExtractionResult(result);
    }

    public ExtractionResult extractFromImage(MultipartFile file) {
        try {
            byte[] imageData = file.getBytes();
            String mimeType = file.getContentType() != null ? file.getContentType() : "image/png";
            JsonNode result = geminiClient.extractFromImage(imageData, mimeType);
            return parseExtractionResult(result);
        } catch (Exception e) {
            throw new RuntimeException("Failed to read uploaded image: " + e.getMessage(), e);
        }
    }

    public List<EventResponse> confirmAndSave(UUID userId, ExtractConfirmRequest request) {
        List<EventResponse> responses = new ArrayList<>();

        for (ExtractConfirmRequest.ConfirmedEvent confirmed : request.getEvents()) {
            Event event = new Event();
            event.setUserId(userId);
            event.setTitle(confirmed.getTitle());
            event.setType(confirmed.getType());
            event.setDueDate(confirmed.getDueDate());
            event.setDueTime(confirmed.getDueTime());
            event.setTimezone(confirmed.getTimezone() != null ? confirmed.getTimezone() : "UTC");
            event.setSource(request.getSourceType() != null ? request.getSourceType() : "pasted_text");
            event.setSourceReference(request.getSourceReference());
            event.setSourceFileUrl(request.getSourceFileUrl());
            event.setConfidenceScore(1.0f);
            event.setStatus(computeStatus(confirmed.getDueDate(), confirmed.getDueTime()));
            event.setReminderSchedule(confirmed.getReminderSchedule() != null
                    ? confirmed.getReminderSchedule() : List.of("7d", "1d", "2h"));
            event.setNotes(confirmed.getNotes());

            Event saved = eventRepository.save(event);
            responses.add(toResponse(saved));
        }

        return responses;
    }

    private ExtractionResult parseExtractionResult(JsonNode result) {
        List<ExtractionResult.ExtractedEvent> events = new ArrayList<>();
        boolean needsConfirmation = false;
        String clarificationQuestion = null;

        if (result.has("clarification_needed") && !result.get("clarification_needed").isNull()) {
            clarificationQuestion = result.get("clarification_needed").asText();
            needsConfirmation = true;
        }

        if (result.has("events") && result.get("events").isArray()) {
            for (JsonNode eventNode : result.get("events")) {
                ExtractionResult.ExtractedEvent extracted = ExtractionResult.ExtractedEvent.builder()
                        .title(getTextOrDefault(eventNode, "title", "Untitled Event"))
                        .type(getTextOrDefault(eventNode, "type", "other"))
                        .dueDate(parseDate(eventNode, "due_date"))
                        .dueTime(parseTime(eventNode, "due_time"))
                        .timezone(getTextOrNull(eventNode, "timezone"))
                        .confidenceScore(eventNode.has("confidence")
                                ? (float) eventNode.get("confidence").asDouble() : 0.5f)
                        .needsClarification(eventNode.has("needs_clarification")
                                && eventNode.get("needs_clarification").asBoolean())
                        .build();

                if (extracted.isNeedsClarification() || extracted.getConfidenceScore() < 0.7f) {
                    needsConfirmation = true;
                }

                events.add(extracted);
            }
        }

        return ExtractionResult.builder()
                .events(events)
                .needsConfirmation(needsConfirmation)
                .clarificationQuestion(clarificationQuestion)
                .build();
    }

    private String getTextOrDefault(JsonNode node, String field, String defaultValue) {
        return node.has(field) && !node.get(field).isNull()
                ? node.get(field).asText() : defaultValue;
    }

    private String getTextOrNull(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull()
                ? node.get(field).asText() : null;
    }

    private LocalDate parseDate(JsonNode node, String field) {
        if (!node.has(field) || node.get(field).isNull()) return null;
        try {
            return LocalDate.parse(node.get(field).asText(), DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (Exception e) {
            return null;
        }
    }

    private LocalTime parseTime(JsonNode node, String field) {
        if (!node.has(field) || node.get(field).isNull()) return null;
        try {
            return LocalTime.parse(node.get(field).asText(), DateTimeFormatter.ofPattern("HH:mm"));
        } catch (Exception e) {
            return null;
        }
    }

    private String computeStatus(LocalDate dueDate, LocalTime dueTime) {
        if (dueDate == null) return "upcoming";
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        java.time.LocalDateTime due = dueTime != null
                ? java.time.LocalDateTime.of(dueDate, dueTime)
                : java.time.LocalDateTime.of(dueDate, LocalTime.MAX);

        if (due.isBefore(now)) return "overdue";
        if (due.minusDays(3).isBefore(now)) return "due_soon";
        return "upcoming";
    }

    private EventResponse toResponse(Event event) {
        return EventResponse.builder()
                .id(event.getId())
                .title(event.getTitle())
                .type(event.getType())
                .dueDate(event.getDueDate())
                .dueTime(event.getDueTime())
                .timezone(event.getTimezone())
                .source(event.getSource())
                .confidenceScore(event.getConfidenceScore())
                .status(event.getStatus())
                .reminderSchedule(event.getReminderSchedule())
                .notes(event.getNotes())
                .sourceFileUrl(event.getSourceFileUrl())
                .createdAt(event.getCreatedAt())
                .updatedAt(event.getUpdatedAt())
                .build();
    }
}
