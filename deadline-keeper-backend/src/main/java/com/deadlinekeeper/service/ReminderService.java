package com.deadlinekeeper.service;

import com.deadlinekeeper.model.Event;
import com.deadlinekeeper.model.NotificationOutbox;
import com.deadlinekeeper.model.Reminder;
import com.deadlinekeeper.model.ReminderDelivery;
import com.deadlinekeeper.model.User;
import com.deadlinekeeper.repository.EventRepository;
import com.deadlinekeeper.repository.NotificationOutboxRepository;
import com.deadlinekeeper.repository.ReminderDeliveryRepository;
import com.deadlinekeeper.repository.ReminderRepository;
import com.deadlinekeeper.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class ReminderService {

    private static final Logger log = LoggerFactory.getLogger(ReminderService.class);
    private static final long MAX_REMINDER_OFFSET_SECONDS = 7 * 86400;

    private final EventRepository eventRepository;
    private final ReminderRepository reminderRepository;
    private final ReminderDeliveryRepository deliveryRepository;
    private final NotificationOutboxRepository outboxRepository;
    private final UserRepository userRepository;
    private final DeadlineStatusService statusService;

    public ReminderService(EventRepository eventRepository,
                           ReminderRepository reminderRepository,
                           ReminderDeliveryRepository deliveryRepository,
                           NotificationOutboxRepository outboxRepository,
                           UserRepository userRepository,
                           DeadlineStatusService statusService) {
        this.eventRepository = eventRepository;
        this.reminderRepository = reminderRepository;
        this.deliveryRepository = deliveryRepository;
        this.outboxRepository = outboxRepository;
        this.userRepository = userRepository;
        this.statusService = statusService;
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

            List<Reminder> reminders = reminderRepository.findByEventId(event.getId());

            for (Reminder reminder : reminders) {
                if (!reminder.getEnabled()) continue;

                Instant fireTime = event.getDueAt().minusSeconds(reminder.getOffsetSeconds());

                if (fireTime.isAfter(now)) continue;

                boolean alreadyDelivered = deliveryRepository.existsByEventIdAndReminderIdAndChannel(
                        event.getId(), reminder.getId(), reminder.getChannel());

                if (alreadyDelivered) continue;

                ReminderDelivery delivery = new ReminderDelivery();
                delivery.setEventId(event.getId());
                delivery.setReminderId(reminder.getId());
                delivery.setScheduledAt(fireTime);
                delivery.setChannel(reminder.getChannel());
                delivery.setStatus("pending");
                delivery.setAttemptCount(0);
                deliveryRepository.save(delivery);

                User user = userRepository.findById(event.getUserId()).orElse(null);
                if (user == null) continue;

                String idempotencyKey = "reminder_%s_%s_%s".formatted(
                        event.getId(), reminder.getId(), reminder.getChannel());

                if (outboxRepository.findByIdempotencyKey(idempotencyKey).isPresent()) continue;

                String timeDesc = formatDuration(Duration.ofSeconds(reminder.getOffsetSeconds()));
                String title = "⏰ Deadline Reminder: " + event.getTitle();
                String message = "Your deadline for \"%s\" is in %s (due: %s).".formatted(
                        event.getTitle(), timeDesc,
                        event.getDueAt().atZone(ZoneId.of(event.getTimezone()))
                                .format(DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm")));

                NotificationOutbox outbox = new NotificationOutbox();
                outbox.setUserId(user.getId());
                outbox.setEventId(event.getId());
                outbox.setTitle(title);
                outbox.setMessage(message);
                outbox.setChannel(reminder.getChannel());
                outbox.setIdempotencyKey(idempotencyKey);
                outbox.setStatus("pending");
                outbox.setAttemptCount(0);
                outbox.setMaxAttempts(3);
                outbox.setScheduledAt(Instant.now());
                outboxRepository.save(outbox);

                delivery.setStatus("sent");
                delivery.setSentAt(Instant.now());
                deliveryRepository.save(delivery);
            }
        }
    }

    @Transactional
    public void syncReminders(Event event, List<String> schedule) {
        reminderRepository.findByEventId(event.getId())
                .forEach(reminderRepository::delete);

        if (schedule == null) return;

        for (String offset : schedule) {
            Duration duration = parseDuration(offset);
            Reminder reminder = new Reminder();
            reminder.setEventId(event.getId());
            reminder.setOffsetSeconds(duration.getSeconds());
            reminder.setChannel("in_app");
            reminder.setEnabled(true);
            reminderRepository.save(reminder);
        }
    }

    private Duration parseDuration(String offset) {
        if (offset.endsWith("d")) {
            return Duration.ofDays(Long.parseLong(offset.replace("d", "")));
        } else if (offset.endsWith("h")) {
            return Duration.ofHours(Long.parseLong(offset.replace("h", "")));
        } else if (offset.endsWith("m")) {
            return Duration.ofMinutes(Long.parseLong(offset.replace("m", "")));
        }
        return Duration.ofDays(1);
    }

    private String formatDuration(Duration duration) {
        long days = duration.toDays();
        long hours = duration.toHoursPart();
        long minutes = duration.toMinutesPart();

        if (days > 0) return days + (days == 1 ? " day" : " days");
        if (hours > 0) return hours + (hours == 1 ? " hour" : " hours");
        return minutes + (minutes == 1 ? " minute" : " minutes");
    }
}
