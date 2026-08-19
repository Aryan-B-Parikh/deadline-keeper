package com.deadlinekeeper.service;

import com.deadlinekeeper.model.Event;
import com.deadlinekeeper.model.Reminder;
import com.deadlinekeeper.repository.ReminderRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class ReminderPlanner {

    private final ReminderRepository reminderRepository;
    private final ReminderDeliveryService deliveryService;

    public ReminderPlanner(ReminderRepository reminderRepository,
                           ReminderDeliveryService deliveryService) {
        this.reminderRepository = reminderRepository;
        this.deliveryService = deliveryService;
    }

    @Transactional
    public void syncFromSchedule(Event event, List<com.deadlinekeeper.dto.ReminderRequest> schedule) {
        reminderRepository.findByEventId(event.getId())
                .forEach(reminderRepository::delete);

        if (schedule == null || schedule.isEmpty()) return;

        for (com.deadlinekeeper.dto.ReminderRequest req : schedule) {
            Reminder reminder = new Reminder();
            reminder.setEventId(event.getId());
            reminder.setOffsetSeconds(req.getOffsetSeconds());
            reminder.setChannel(req.getChannel());
            reminder.setEnabled(true);
            reminderRepository.save(reminder);
        }
    }

    @Transactional
    public void planReminders(Event event) {
        List<Reminder> reminders = reminderRepository.findByEventId(event.getId());
        Instant now = Instant.now();

        for (Reminder reminder : reminders) {
            if (!reminder.getEnabled()) continue;

            Instant fireTime = event.getDueAt().minusSeconds(reminder.getOffsetSeconds());
            if (fireTime.isAfter(now)) continue;

            try {
                deliveryService.createDeliveryIfAbsent(event, reminder, fireTime);
            } catch (DataIntegrityViolationException duplicate) {
                // Another scheduler instance won the unique delivery race.
            }
        }
    }
}
