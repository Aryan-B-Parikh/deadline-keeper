package com.deadlinekeeper.service;

import com.deadlinekeeper.model.Event;
import com.deadlinekeeper.repository.EventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class ReminderService {

    private static final Logger log = LoggerFactory.getLogger(ReminderService.class);
    private static final long MAX_REMINDER_OFFSET_SECONDS = 7 * 86400;

    private final ReminderPlanner reminderPlanner;
    private final ReminderDeliveryService deliveryService;
    private final NotificationOutboxService outboxService;
    private final DeadlineStatusService statusService;
    private final EventRepository eventRepository;

    public ReminderService(ReminderPlanner reminderPlanner,
                           ReminderDeliveryService deliveryService,
                           NotificationOutboxService outboxService,
                           DeadlineStatusService statusService,
                           EventRepository eventRepository) {
        this.reminderPlanner = reminderPlanner;
        this.deliveryService = deliveryService;
        this.outboxService = outboxService;
        this.statusService = statusService;
        this.eventRepository = eventRepository;
    }

    @Transactional
    public void processReminders() {
        Instant now = Instant.now();
        Instant windowEnd = now.plusSeconds(MAX_REMINDER_OFFSET_SECONDS);

        List<Event> activeEvents = eventRepository.findActiveBetween(now, windowEnd);
        List<Event> overdueEvents = eventRepository.findPendingBefore(now);

        Set<UUID> processedEventIds = new HashSet<>();
        List<Event> allEvents = new ArrayList<>();
        allEvents.addAll(activeEvents);
        allEvents.addAll(overdueEvents);

        for (Event event : allEvents) {
            if (processedEventIds.contains(event.getId())) continue;
            processedEventIds.add(event.getId());

            String newStatus = statusService.computeStatus(event.getDueAt(), event.getStatus());
            if (!newStatus.equals(event.getStatus())) {
                event.setStatus(newStatus);
                eventRepository.save(event);
            }

            if ("done".equals(event.getStatus())) continue;

            reminderPlanner.planReminders(event);
        }
    }

    public void syncReminders(Event event, List<String> schedule) {
        reminderPlanner.syncFromSchedule(event, schedule);
    }
}