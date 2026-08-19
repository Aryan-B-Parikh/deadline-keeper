package com.deadlinekeeper.service;

import com.deadlinekeeper.dto.ExtractConfirmRequest;
import com.deadlinekeeper.dto.EventResponse;
import com.deadlinekeeper.dto.ExtractionResult;
import com.deadlinekeeper.exception.ExternalServiceException;
import com.deadlinekeeper.exception.ValidationException;
import com.deadlinekeeper.integration.GeminiClient;
import com.deadlinekeeper.model.Event;
import com.deadlinekeeper.repository.EventRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ExtractionService {

    private final GeminiClient geminiClient;
    private final EventRepository eventRepository;
    private final DeadlineStatusService deadlineStatusService;
    private final ReminderService reminderService;
    private final com.deadlinekeeper.mapper.EventCompatibilityMapper eventCompatibilityMapper;

    public ExtractionService(GeminiClient geminiClient, EventRepository eventRepository,
                             DeadlineStatusService deadlineStatusService,
                             ReminderService reminderService,
                             com.deadlinekeeper.mapper.EventCompatibilityMapper eventCompatibilityMapper) {
        this.geminiClient = geminiClient;
        this.eventRepository = eventRepository;
        this.deadlineStatusService = deadlineStatusService;
        this.reminderService = reminderService;
        this.eventCompatibilityMapper = eventCompatibilityMapper;
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
        } catch (ExternalServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalServiceException("Gemini", "Failed to read uploaded image: " + e.getMessage(), e);
        }
    }

    public List<EventResponse> confirmAndSave(UUID userId, ExtractConfirmRequest request) {
        List<EventResponse> responses = new ArrayList<>();

        for (ExtractConfirmRequest.ConfirmedEvent confirmed : request.getEvents()) {
            if (confirmed.getTitle() == null || confirmed.getTitle().isBlank()) {
                throw new ValidationException("Event title is required");
            }
            if (confirmed.getDueAt() == null) {
                throw new ValidationException("DueAt is required for event: " + confirmed.getTitle());
            }

            String tz = confirmed.getTimezone() != null ? confirmed.getTimezone() : "UTC";
            ZoneId zone;
            try {
                zone = ZoneId.of(tz);
            } catch (Exception e) {
                zone = ZoneOffset.UTC;
                tz = "UTC";
            }

            Instant dueAt = confirmed.getDueAt();

            Event event = new Event();
            event.setUserId(userId);
            event.setTitle(confirmed.getTitle());
            event.setType(confirmed.getType() != null ? confirmed.getType() : "other");
            event.setDueAt(dueAt);
            event.setTimezone(tz);
            event.setSource(request.getSourceType() != null ? request.getSourceType() : "pasted_text");

            String sourceRef = request.getSourceReference();
            if (sourceRef != null && sourceRef.length() > 200) {
                sourceRef = sourceRef.substring(0, 200) + "...";
            }
            event.setSourceReference(sourceRef);

            String sourceUrl = request.getSourceFileUrl();
            if (sourceUrl != null && sourceUrl.length() > 500) {
                sourceUrl = sourceUrl.substring(0, 500);
            }
            event.setSourceFileUrl(sourceUrl);
            event.setAiConfidence(1.0f);
            event.setConfirmationStatus("user_confirmed");
            event.setUserConfirmed(true);
            event.setStatus(deadlineStatusService.computeStatus(dueAt));
            event.setNotes(confirmed.getNotes());

            Event saved = eventRepository.save(event);
            if (confirmed.getReminders() != null) {
                reminderService.syncFromSchedule(saved, confirmed.getReminders());
            }
            responses.add(eventCompatibilityMapper.toResponse(saved));
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
                LocalDate parsedDate = parseDate(eventNode, "due_date");
                LocalTime parsedTime = parseTime(eventNode, "due_time");
                String parsedZone = getTextOrNull(eventNode, "timezone");
                if (parsedZone == null) parsedZone = "UTC";
                
                Instant computedDueAt = null;
                if (parsedDate != null) {
                    ZoneId zoneId = ZoneId.of(parsedZone);
                    if (parsedTime != null) {
                        computedDueAt = parsedDate.atTime(parsedTime).atZone(zoneId).toInstant();
                    } else {
                        computedDueAt = parsedDate.atStartOfDay(zoneId).toInstant();
                    }
                }
                
                float conf = eventNode.has("confidence") ? (float) eventNode.get("confidence").asDouble() : 0.5f;

                ExtractionResult.ExtractedEvent extracted = ExtractionResult.ExtractedEvent.builder()
                        .title(getTextOrDefault(eventNode, "title", "Untitled Event"))
                        .type(getTextOrDefault(eventNode, "type", "other"))
                        .dueAt(computedDueAt)
                        .timezone(parsedZone)
                        .aiConfidence(conf)
                        .needsClarification(eventNode.has("needs_clarification")
                                && eventNode.get("needs_clarification").asBoolean())
                        .build();

                // Skip events with no date — they can't be deadlines
                if (extracted.getDueAt() == null) {
                    continue;
                }

                if (extracted.isNeedsClarification() || extracted.getAiConfidence() < 0.7f) {
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

}
