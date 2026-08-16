package com.deadlinekeeper.scheduler;

import com.deadlinekeeper.service.ReminderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReminderScheduler.class);

    private final ReminderService reminderService;

    public ReminderScheduler(ReminderService reminderService) {
        this.reminderService = reminderService;
    }

    @Scheduled(fixedRate = 3600000)
    public void checkReminders() {
        log.info("Running reminder check...");
        try {
            reminderService.processReminders();
            log.info("Reminder check completed.");
        } catch (Exception e) {
            log.error("Reminder check failed: {}", e.getMessage(), e);
        }
    }
}
