package com.deadlinekeeper.mapper;

import com.deadlinekeeper.dto.EventResponse;
import com.deadlinekeeper.dto.ReminderResponse;
import com.deadlinekeeper.model.Event;
import com.deadlinekeeper.repository.ReminderRepository;
import org.springframework.stereotype.Component;

@Component
public class EventMapper {

    private final ReminderRepository reminderRepository;

    public EventMapper(ReminderRepository reminderRepository) {
        this.reminderRepository = reminderRepository;
    }

    public EventResponse toResponse(Event event) {
        var reminders = reminderRepository.findByEventId(event.getId())
                .stream()
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
                .reminders(reminders)
                .notes(event.getNotes())
                .sourceFileUrl(event.getSourceFileUrl())
                .createdAt(event.getCreatedAt())
                .updatedAt(event.getUpdatedAt())
                .build();
    }
}
