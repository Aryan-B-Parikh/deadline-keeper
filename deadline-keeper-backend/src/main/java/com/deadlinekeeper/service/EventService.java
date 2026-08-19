package com.deadlinekeeper.service;

import com.deadlinekeeper.dto.EventRequest;
import com.deadlinekeeper.dto.EventResponse;
import com.deadlinekeeper.exception.ResourceNotFoundException;
import com.deadlinekeeper.exception.ValidationException;
import com.deadlinekeeper.mapper.EventMapper;
import com.deadlinekeeper.model.Event;
import com.deadlinekeeper.repository.EventRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
public class EventService {

    private final EventRepository eventRepository;
    private final DeadlineStatusService deadlineStatusService;
    private final ReminderService reminderService;
    private final EventMapper eventMapper;

    public EventService(EventRepository eventRepository,
                        DeadlineStatusService deadlineStatusService,
                        ReminderService reminderService,
                        EventMapper eventMapper) {
        this.eventRepository = eventRepository;
        this.deadlineStatusService = deadlineStatusService;
        this.reminderService = reminderService;
        this.eventMapper = eventMapper;
    }

    public List<EventResponse> getUserEvents(UUID userId, String status) {
        List<Event> events = status != null && !status.isEmpty()
                ? eventRepository.findByUserIdAndStatus(userId, status)
                : eventRepository.findByUserId(userId);
        return eventMapper.toResponses(events);
    }

    public EventResponse getEvent(UUID userId, UUID eventId) {
        Event event = eventRepository.findByIdAndUserId(eventId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Event", eventId.toString()));
        return eventMapper.toResponse(event);
    }

    public EventResponse createEvent(UUID userId, EventRequest request) {
        Event event = new Event();
        event.setUserId(userId);
        event.setTitle(request.getTitle());
        event.setType(request.getType());
        event.setTimezone(validateTimezone(request.getTimezone()));
        event.setDueAt(request.getDueAt());
        event.setSource("manual");
        event.setAiConfidence(null);
        event.setStatus(deadlineStatusService.computeStatus(event.getDueAt()));
        event.setNotes(request.getNotes());

        Event saved = eventRepository.save(event);
        if (request.getReminders() != null) reminderService.syncFromSchedule(saved, request.getReminders());
        return eventMapper.toResponse(saved);
    }

    public EventResponse updateEvent(UUID userId, UUID eventId, EventRequest request) {
        Event event = eventRepository.findByIdAndUserId(eventId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Event", eventId.toString()));

        event.setTitle(request.getTitle());
        event.setType(request.getType());
        event.setTimezone(validateTimezone(request.getTimezone()));
        event.setNotes(request.getNotes());
        event.setDueAt(request.getDueAt());

        if (!"done".equals(event.getStatus())) {
            event.setStatus(deadlineStatusService.computeStatus(event.getDueAt(), event.getStatus()));
        }

        Event saved = eventRepository.save(event);
        if (request.getReminders() != null) reminderService.syncFromSchedule(saved, request.getReminders());
        return eventMapper.toResponse(saved);
    }

    public void deleteEvent(UUID userId, UUID eventId) {
        Event event = eventRepository.findByIdAndUserId(eventId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Event", eventId.toString()));
        eventRepository.delete(event);
    }

    public EventResponse markAsDone(UUID userId, UUID eventId) {
        Event event = eventRepository.findByIdAndUserId(eventId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Event", eventId.toString()));
        event.setStatus("done");
        return eventMapper.toResponse(eventRepository.save(event));
    }

    public EventResponse snoozeEvent(UUID userId, UUID eventId, String duration) {
        Event event = eventRepository.findByIdAndUserId(eventId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Event", eventId.toString()));

        Duration snoozeDuration = parseDuration(duration);
        Instant newDueAt = event.getDueAt().plus(snoozeDuration);
        event.setDueAt(newDueAt);
        event.setStatus(deadlineStatusService.computeStatus(newDueAt, event.getStatus()));

        return eventMapper.toResponse(eventRepository.save(event));
    }

    private String validateTimezone(String timezone) {
        String value = timezone == null || timezone.isBlank() ? "UTC" : timezone;
        try {
            ZoneId.of(value);
            return value;
        } catch (Exception e) {
            throw new ValidationException("Invalid timezone: " + value);
        }
    }

    private Duration parseDuration(String duration) {
        if (duration == null || duration.isBlank()) return Duration.ofDays(1);
        try {
            if (duration.endsWith("d")) return Duration.ofDays(Long.parseLong(duration.substring(0, duration.length() - 1)));
            if (duration.endsWith("h")) return Duration.ofHours(Long.parseLong(duration.substring(0, duration.length() - 1)));
            if (duration.endsWith("m")) return Duration.ofMinutes(Long.parseLong(duration.substring(0, duration.length() - 1)));
        } catch (NumberFormatException e) {
            throw new ValidationException("Invalid snooze duration: " + duration);
        }
        throw new ValidationException("Invalid snooze duration: " + duration);
    }
}
