package com.deadlinekeeper.service;

import com.deadlinekeeper.dto.EventRequest;
import com.deadlinekeeper.dto.EventResponse;
import com.deadlinekeeper.exception.ResourceNotFoundException;
import com.deadlinekeeper.mapper.EventCompatibilityMapper;
import com.deadlinekeeper.model.Event;
import com.deadlinekeeper.repository.EventRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
public class EventService {

    private final EventRepository eventRepository;
    private final DeadlineStatusService deadlineStatusService;
    private final ReminderService reminderService;
    private final EventCompatibilityMapper eventMapper;

    public EventService(EventRepository eventRepository,
                        DeadlineStatusService deadlineStatusService,
                        ReminderService reminderService,
                        EventCompatibilityMapper eventMapper) {
        this.eventRepository = eventRepository;
        this.deadlineStatusService = deadlineStatusService;
        this.reminderService = reminderService;
        this.eventMapper = eventMapper;
    }

    public List<EventResponse> getUserEvents(UUID userId, String status) {
        List<Event> events;
        if (status != null && !status.isEmpty()) {
            events = eventRepository.findByUserIdAndStatus(userId, status);
        } else {
            events = eventRepository.findByUserId(userId);
        }
        return events.stream().map(eventMapper::toResponse).toList();
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

        String timezone = request.getTimezone() != null ? request.getTimezone() : "UTC";
        event.setTimezone(timezone);
        event.setDueAt(eventMapper.resolveCanonicalDueAt(request));

        event.setSource("manual");
        event.setAiConfidence(null); // Explicitly null for manual
        event.setStatus(deadlineStatusService.computeStatus(event.getDueAt()));
        event.setNotes(request.getNotes());

        Event saved = eventRepository.save(event);
        List<String> schedule = request.getReminderSchedule() != null
                ? request.getReminderSchedule() : List.of("7d", "1d", "2h");
        reminderService.syncReminders(saved, schedule);
        return eventMapper.toResponse(saved);
    }

    public EventResponse updateEvent(UUID userId, UUID eventId, EventRequest request) {
        Event event = eventRepository.findByIdAndUserId(eventId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Event", eventId.toString()));

        event.setTitle(request.getTitle());
        event.setType(request.getType());
        if (request.getTimezone() != null) event.setTimezone(request.getTimezone());
        event.setNotes(request.getNotes());

        event.setDueAt(eventMapper.resolveCanonicalDueAt(request));

        if (!event.getStatus().equals("done")) {
            event.setStatus(deadlineStatusService.computeStatus(event.getDueAt(), event.getStatus()));
        }

        Event saved = eventRepository.save(event);
        if (request.getReminderSchedule() != null) {
            reminderService.syncReminders(saved, request.getReminderSchedule());
        }
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

        Event saved = eventRepository.save(event);
        return eventMapper.toResponse(saved);
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


}
