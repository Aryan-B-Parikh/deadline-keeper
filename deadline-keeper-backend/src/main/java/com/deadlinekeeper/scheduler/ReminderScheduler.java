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

    @Scheduled(fixedRate = 60000) // every minute
    public void processOutbox() {
        try {
            outboxProcessor.processPending();
        } catch (Exception e) {
            log.error("Outbox processing failed: {}", e.getMessage(), e);
        }
    }

    /**
     * Watchdog: reclaim expired leases every 30 seconds.
     * Faster than the main loop to minimize stuck time after a crash.
     */
    @Scheduled(fixedRate = 30000)
    public void watchdogReclaimExpiredLeases() {
        try {
            int reclaimed = outboxProcessor.reclaimExpiredLeases();
            if (reclaimed > 0) {
                log.warn("Watchdog reclaimed {} expired processing leases", reclaimed);
            }
        } catch (Exception e) {
            log.error("Watchdog failed: {}", e.getMessage(), e);
        }
    }
}
