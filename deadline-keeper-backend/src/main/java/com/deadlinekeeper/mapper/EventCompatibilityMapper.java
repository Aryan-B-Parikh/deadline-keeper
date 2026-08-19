package com.deadlinekeeper.mapper;

import com.deadlinekeeper.dto.EventRequest;
import com.deadlinekeeper.dto.EventResponse;
import com.deadlinekeeper.model.Event;
import org.springframework.stereotype.Component;
import com.deadlinekeeper.dto.ReminderResponse;
import com.deadlinekeeper.repository.ReminderRepository;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class EventCompatibilityMapper {

    private final ReminderRepository reminderRepository;

    public EventCompatibilityMapper(ReminderRepository reminderRepository) {
        this.reminderRepository = reminderRepository;
    }

    public EventResponse toResponse(Event event) {
        List<ReminderResponse> reminders = reminderRepository.findByEventId(event.getId())
                .stream()
                .map(r -> new ReminderResponse(r.getId(), r.getOffsetSeconds(), r.getChannel(), r.getEnabled()))
                .collect(Collectors.toList());

        return EventResponse.builder()
                .id(event.getId())
                .title(event.getTitle())
                .type(event.getType())
                .dueAt(event.getDueAt())
                .timezone(event.getTimezone())
                .source(event.getSource())
                .aiConfidence(event.getAiConfidence() != null ? event.getAiConfidence() : 1.0f)
                .status(event.getStatus())
                .reminders(reminders)
                .notes(event.getNotes())
                .sourceFileUrl(event.getSourceFileUrl())
                .createdAt(event.getCreatedAt())
                .updatedAt(event.getUpdatedAt())
                .build();
    }
}
