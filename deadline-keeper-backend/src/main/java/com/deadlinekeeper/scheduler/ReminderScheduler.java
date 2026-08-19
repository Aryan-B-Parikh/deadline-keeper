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
    private final NotificationOutboxProcessor outboxProcessor;

    public ReminderScheduler(ReminderService reminderService,
                             NotificationOutboxProcessor outboxProcessor) {
        this.reminderService = reminderService;
        this.outboxProcessor = outboxProcessor;
    }

    @Scheduled(fixedRate = 900000) // every 15 minutes
    public void checkReminders() {
        log.info("Running reminder check...");
        try {
            reminderService.processReminders();
            log.info("Reminder check completed.");
        } catch (Exception e) {
            log.error("Reminder check failed: {}", e.getMessage(), e);
        }
    }

    @Scheduled(fixedRate = 60000) // every minute for outbox
    public void processOutbox() {
        try {
            outboxProcessor.processPending();
        } catch (Exception e) {
            log.error("Outbox processing failed: {}", e.getMessage(), e);
        }
    }
}
