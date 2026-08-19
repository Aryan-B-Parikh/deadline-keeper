package com.deadlinekeeper.service;

import com.deadlinekeeper.model.Event;
import com.deadlinekeeper.model.Reminder;
import com.deadlinekeeper.model.ReminderDelivery;
import com.deadlinekeeper.model.User;
import com.deadlinekeeper.repository.ReminderDeliveryRepository;
import com.deadlinekeeper.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Service
public class ReminderDeliveryService {

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

    /**
     * Creates one delivery in an independent transaction. The unique database constraint
     * handles concurrent schedulers; a duplicate simply rolls back this small transaction.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createDeliveryIfAbsent(Event event, Reminder reminder, Instant fireTime) {
        if (deliveryRepository.existsByEventIdAndReminderIdAndChannel(
                event.getId(), reminder.getId(), reminder.getChannel())) return;

        ReminderDelivery delivery = new ReminderDelivery();
        delivery.setEventId(event.getId());
        delivery.setReminderId(reminder.getId());
        delivery.setScheduledAt(fireTime);
        delivery.setChannel(reminder.getChannel());
        delivery.setStatus("pending");
        delivery.setAttemptCount(0);

        deliveryRepository.saveAndFlush(delivery);

        User user = userRepository.findById(event.getUserId()).orElse(null);
        if (user == null) return;

        String timeDesc = formatDuration(Duration.ofSeconds(reminder.getOffsetSeconds()));
        String title = "\u23F0 Deadline Reminder: " + event.getTitle();
        String message = "Your deadline for \"%s\" is in %s (due: %s).".formatted(
                event.getTitle(), timeDesc,
                event.getDueAt().atZone(ZoneId.of(event.getTimezone()))
                        .format(DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm")));

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
