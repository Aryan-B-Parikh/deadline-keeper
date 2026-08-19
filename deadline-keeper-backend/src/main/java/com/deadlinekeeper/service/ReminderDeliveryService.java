package com.deadlinekeeper.service;

import com.deadlinekeeper.model.Event;
import com.deadlinekeeper.model.Reminder;
import com.deadlinekeeper.model.ReminderDelivery;
import com.deadlinekeeper.model.User;
import com.deadlinekeeper.repository.ReminderDeliveryRepository;
import com.deadlinekeeper.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class ReminderDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(ReminderDeliveryService.class);

    private final ReminderDeliveryRepository deliveryRepository;
    private final NotificationOutboxService outboxService;
    private final UserRepository userRepository;

    public ReminderDeliveryService(ReminderDeliveryRepository deliveryRepository,
                                   NotificationOutboxService outboxService,
                                   UserRepository userRepository) {
        this.deliveryRepository = deliveryRepository;
        this.outboxService = outboxService;
        this.userRepository = userRepository;
    }

    @Transactional
    public void createDeliveryIfAbsent(Event event, Reminder reminder, Instant fireTime) {
        boolean exists = deliveryRepository.existsByEventIdAndReminderIdAndChannel(
                event.getId(), reminder.getId(), reminder.getChannel());
        if (exists) return;

        ReminderDelivery delivery = new ReminderDelivery();
        delivery.setEventId(event.getId());
        delivery.setReminderId(reminder.getId());
        delivery.setScheduledAt(fireTime);
        delivery.setChannel(reminder.getChannel());
        delivery.setStatus("pending");
        delivery.setAttemptCount(0);

        try {
            deliveryRepository.save(delivery);
        } catch (DataIntegrityViolationException e) {
            log.debug("Race condition: another instance already created delivery for event {} reminder {}",
                    event.getId(), reminder.getId());
            return;
        }

        User user = userRepository.findById(event.getUserId()).orElse(null);
        if (user == null) return;

        String timeDesc = formatDuration(Duration.ofSeconds(reminder.getOffsetSeconds()));
        String title = "\u23F0 Deadline Reminder: " + event.getTitle();
        String message = "Your deadline for \"%s\" is in %s (due: %s).".formatted(
                event.getTitle(), timeDesc,
                event.getDueAt().atZone(ZoneId.of(event.getTimezone()))
                        .format(DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm")));

        // Enqueue to outbox — delivery status stays PENDING until outbox processor confirms
        outboxService.enqueue(delivery.getId(), user.getId(), event.getId(), title, message, reminder.getChannel(), fireTime);
    }

    private String formatDuration(Duration duration) {
        long days = duration.toDays();
        long hours = duration.toHoursPart();

        if (days > 0) return days + (days == 1 ? " day" : " days");
        if (hours > 0) return hours + (hours == 1 ? " hour" : " hours");
        return duration.toMinutesPart() + " minutes";
    }
}