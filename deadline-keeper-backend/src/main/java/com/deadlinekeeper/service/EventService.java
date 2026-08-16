package com.deadlinekeeper.service;

import com.deadlinekeeper.dto.EventRequest;
import com.deadlinekeeper.dto.EventResponse;
import com.deadlinekeeper.model.Event;
import com.deadlinekeeper.repository.EventRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Service
public class EventService {

    private final EventRepository eventRepository;

    public EventService(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    public List<EventResponse> getUserEvents(UUID userId, String status) {
        List<Event> events;
        if (status != null && !status.isEmpty()) {
            events = eventRepository.findByUserIdAndStatus(userId, status);
        } else {
            events = eventRepository.findByUserId(userId);
        }
        return events.stream().map(this::toResponse).toList();
    }

    public EventResponse getEvent(UUID userId, UUID eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Event not found"));
        if (!event.getUserId().equals(userId)) {
            throw new RuntimeException("Access denied");
        }
        return toResponse(event);
    }

    public EventResponse createEvent(UUID userId, EventRequest request) {
        Event event = new Event();
        event.setUserId(userId);
        event.setTitle(request.getTitle());
        event.setType(request.getType());
        event.setDueDate(request.getDueDate());
        event.setDueTime(request.getDueTime());
        event.setTimezone(request.getTimezone() != null ? request.getTimezone() : "UTC");
        event.setSource("manual");
        event.setConfidenceScore(1.0f);
        event.setStatus(computeStatus(request.getDueDate(), request.getDueTime()));
        event.setReminderSchedule(request.getReminderSchedule() != null
                ? request.getReminderSchedule() : List.of("7d", "1d", "2h"));
        event.setNotes(request.getNotes());
        return toResponse(eventRepository.save(event));
    }

    public EventResponse updateEvent(UUID userId, UUID eventId, EventRequest request) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Event not found"));
        if (!event.getUserId().equals(userId)) {
            throw new RuntimeException("Access denied");
        }

        event.setTitle(request.getTitle());
        event.setType(request.getType());
        event.setDueDate(request.getDueDate());
        event.setDueTime(request.getDueTime());
        if (request.getTimezone() != null) event.setTimezone(request.getTimezone());
        if (request.getReminderSchedule() != null) event.setReminderSchedule(request.getReminderSchedule());
        event.setNotes(request.getNotes());

        if (!event.getStatus().equals("done")) {
            event.setStatus(computeStatus(request.getDueDate(), request.getDueTime()));
        }

        return toResponse(eventRepository.save(event));
    }

    public void deleteEvent(UUID userId, UUID eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Event not found"));
        if (!event.getUserId().equals(userId)) {
            throw new RuntimeException("Access denied");
        }
        eventRepository.delete(event);
    }

    public EventResponse markAsDone(UUID userId, UUID eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Event not found"));
        if (!event.getUserId().equals(userId)) {
            throw new RuntimeException("Access denied");
        }
        event.setStatus("done");
        return toResponse(eventRepository.save(event));
    }

    public EventResponse snoozeEvent(UUID userId, UUID eventId, String duration) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Event not found"));
        if (!event.getUserId().equals(userId)) {
            throw new RuntimeException("Access denied");
        }

        Duration snoozeDuration = parseDuration(duration);
        event.setDueDate(event.getDueDate().plusDays(snoozeDuration.toDays()));
        if (snoozeDuration.toHoursPart() > 0 && event.getDueTime() != null) {
            event.setDueTime(event.getDueTime().plusHours(snoozeDuration.toHoursPart())
                    .plusMinutes(snoozeDuration.toMinutesPart()));
        }
        event.setStatus(computeStatus(event.getDueDate(), event.getDueTime()));
        return toResponse(eventRepository.save(event));
    }

    private String computeStatus(LocalDate dueDate, LocalTime dueTime) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime due = dueTime != null
                ? LocalDateTime.of(dueDate, dueTime)
                : LocalDateTime.of(dueDate, LocalTime.MAX);

        if (due.isBefore(now)) return "overdue";
        if (due.minusDays(3).isBefore(now)) return "due_soon";
        return "upcoming";
    }

    private Duration parseDuration(String duration) {
        if (duration.endsWith("d")) {
            return Duration.ofDays(Long.parseLong(duration.replace("d", "")));
        } else if (duration.endsWith("h")) {
            return Duration.ofHours(Long.parseLong(duration.replace("h", "")));
        } else if (duration.endsWith("m")) {
            return Duration.ofMinutes(Long.parseLong(duration.replace("m", "")));
        }
        return Duration.ofDays(1);
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
