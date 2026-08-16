package com.deadlinekeeper.service;

import com.deadlinekeeper.model.Event;
import com.deadlinekeeper.model.ReminderLog;
import com.deadlinekeeper.model.User;
import com.deadlinekeeper.repository.EventRepository;
import com.deadlinekeeper.repository.ReminderLogRepository;
import com.deadlinekeeper.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
public class ReminderService {

    private final EventRepository eventRepository;
    private final ReminderLogRepository reminderLogRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public ReminderService(EventRepository eventRepository,
                           ReminderLogRepository reminderLogRepository,
                           UserRepository userRepository,
                           NotificationService notificationService) {
        this.eventRepository = eventRepository;
        this.reminderLogRepository = reminderLogRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    public void processReminders() {
        List<Event> activeEvents = eventRepository.findAllActiveEvents();

        for (Event event : activeEvents) {
            User user = userRepository.findById(event.getUserId()).orElse(null);
            if (user == null) continue;

            ZonedDateTime dueDateTime = getDueDateTime(event);
            ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);

            updateEventStatus(event, now, dueDateTime);

            if (event.getReminderSchedule() == null) continue;

            for (String offset : event.getReminderSchedule()) {
                if (reminderLogRepository.existsByEventIdAndOffsetFired(event.getId(), offset)) {
                    continue;
                }

                Duration offsetDuration = parseOffset(offset);
                ZonedDateTime fireTime = dueDateTime.minus(offsetDuration);

                if (!now.isBefore(fireTime)) {
                    fireReminder(user, event, offset, offsetDuration);

                    ReminderLog log = new ReminderLog();
                    log.setEventId(event.getId());
                    log.setOffsetFired(offset);
                    reminderLogRepository.save(log);
                }
            }
        }
    }

    private void updateEventStatus(Event event, ZonedDateTime now, ZonedDateTime dueDateTime) {
        String newStatus;
        if (now.isAfter(dueDateTime)) {
            newStatus = "overdue";
        } else if (now.isAfter(dueDateTime.minusDays(3))) {
            newStatus = "due_soon";
        } else {
            newStatus = "upcoming";
        }

        if (!event.getStatus().equals(newStatus) && !event.getStatus().equals("done")) {
            event.setStatus(newStatus);
            eventRepository.save(event);
        }
    }

    private void fireReminder(User user, Event event, String offset, Duration offsetDuration) {
        String timeDescription = formatDuration(offsetDuration);
        String title = "⏰ Deadline Reminder: " + event.getTitle();
        String message = "Your deadline for \"%s\" is in %s (due: %s%s).".formatted(
                event.getTitle(),
                timeDescription,
                event.getDueDate().toString(),
                event.getDueTime() != null ? " at " + event.getDueTime() : ""
        );

        notificationService.send(user, title, message, event.getId());
    }

    private ZonedDateTime getDueDateTime(Event event) {
        ZoneId zone;
        try {
            zone = ZoneId.of(event.getTimezone());
        } catch (Exception e) {
            zone = ZoneOffset.UTC;
        }

        LocalTime time = event.getDueTime() != null ? event.getDueTime() : LocalTime.of(23, 59);
        LocalDateTime localDateTime = LocalDateTime.of(event.getDueDate(), time);
        return localDateTime.atZone(zone);
    }

    private Duration parseOffset(String offset) {
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
