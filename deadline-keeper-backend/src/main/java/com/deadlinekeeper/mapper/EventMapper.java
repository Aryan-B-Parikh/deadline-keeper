package com.deadlinekeeper.mapper;

import com.deadlinekeeper.dto.EventResponse;
import com.deadlinekeeper.dto.ReminderResponse;
import com.deadlinekeeper.model.Event;
import com.deadlinekeeper.model.Reminder;
import com.deadlinekeeper.repository.ReminderRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class EventMapper {

    private final ReminderRepository reminderRepository;

    public EventMapper(ReminderRepository reminderRepository) {
        this.reminderRepository = reminderRepository;
    }

    public EventResponse toResponse(Event event) {
        return toResponse(event, reminderRepository.findByEventId(event.getId()));
    }

    public List<EventResponse> toResponses(List<Event> events) {
        if (events.isEmpty()) return List.of();

        List<Reminder> reminders = reminderRepository.findByEventIdIn(
                events.stream().map(Event::getId).toList());
        Map<java.util.UUID, List<Reminder>> remindersByEvent = reminders.stream()
                .collect(Collectors.groupingBy(Reminder::getEventId));

        return events.stream()
                .map(event -> toResponse(event, remindersByEvent.getOrDefault(event.getId(), List.of())))
                .toList();
    }

    private EventResponse toResponse(Event event, List<Reminder> reminders) {
        List<ReminderResponse> reminderResponses = reminders.stream()
                .map(r -> new ReminderResponse(r.getId(), r.getOffsetSeconds(), r.getChannel(), r.getEnabled()))
                .toList();

        return EventResponse.builder()
                .id(event.getId())
                .title(event.getTitle())
                .type(event.getType())
                .dueAt(event.getDueAt())
                .timezone(event.getTimezone())
                .source(event.getSource())
                .aiConfidence(event.getAiConfidence())
                .status(event.getStatus())
                .reminders(reminderResponses)
                .notes(event.getNotes())
                .sourceFileUrl(event.getSourceFileUrl())
                .createdAt(event.getCreatedAt())
                .updatedAt(event.getUpdatedAt())
                .build();
    }
}
