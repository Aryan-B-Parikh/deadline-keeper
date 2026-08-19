package com.deadlinekeeper.scheduler;

import com.deadlinekeeper.model.Notification;
import com.deadlinekeeper.model.NotificationOutbox;
import com.deadlinekeeper.model.ReminderDelivery;
import com.deadlinekeeper.model.User;
import com.deadlinekeeper.notification.NotificationChannel;
import com.deadlinekeeper.repository.NotificationOutboxRepository;
import com.deadlinekeeper.repository.NotificationRepository;
import com.deadlinekeeper.repository.ReminderDeliveryRepository;
import com.deadlinekeeper.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
public class NotificationOutboxProcessor {

    private static final Logger log = LoggerFactory.getLogger(NotificationOutboxProcessor.class);

    private final NotificationOutboxRepository outboxRepository;
    private final NotificationRepository notificationRepository;
    private final ReminderDeliveryRepository deliveryRepository;
    private final UserRepository userRepository;
    private final List<NotificationChannel> channels;

    public NotificationOutboxProcessor(NotificationOutboxRepository outboxRepository,
                                       NotificationRepository notificationRepository,
                                       ReminderDeliveryRepository deliveryRepository,
                                       UserRepository userRepository,
                                       List<NotificationChannel> channels) {
        this.outboxRepository = outboxRepository;
        this.notificationRepository = notificationRepository;
        this.deliveryRepository = deliveryRepository;
        this.userRepository = userRepository;
        this.channels = channels;
    }

    @Transactional
    public void processPending() {
        // Step 1: Atomically claim pending jobs (FOR UPDATE SKIP LOCKED)
        int claimed = outboxRepository.claimPendingJobs(50);
        if (claimed == 0) return;

        log.debug("Claimed {} outbox jobs", claimed);

        // Step 2: Fetch the claimed (now-processing) rows
        List<NotificationOutbox> processing = outboxRepository.findByStatusOrderByScheduledAtAsc(
                "processing", PageRequest.of(0, 50));

        // Step 3: Process each claimed job
        for (NotificationOutbox entry : processing) {
            try {
                processEntry(entry);
            } catch (Exception e) {
                log.error("Outbox job {} failed: {}", entry.getId(), e.getMessage(), e);
                handleFailure(entry, e.getMessage());
            }
        }
    }

    private void processEntry(NotificationOutbox entry) {
        // Validate deliveryId exists
        if (entry.getDeliveryId() == null) {
            log.error("Outbox entry {} has no deliveryId — orphan, marking failed", entry.getId());
            entry.setStatus("failed");
            entry.setLastError("No deliveryId");
            outboxRepository.save(entry);
            return;
        }

        // Look up delivery by ID (the only correct identity)
        ReminderDelivery delivery = deliveryRepository.findById(entry.getDeliveryId()).orElse(null);
        if (delivery == null) {
            log.error("Delivery {} not found for outbox {}, marking failed", entry.getDeliveryId(), entry.getId());
            entry.setStatus("failed");
            entry.setLastError("Delivery not found");
            outboxRepository.save(entry);
            return;
        }

        // Skip if delivery already terminal (race: another worker processed it)
        if ("sent".equals(delivery.getStatus()) || "failed".equals(delivery.getStatus())) {
            entry.setStatus("failed");
            entry.setLastError("Delivery already " + delivery.getStatus());
            outboxRepository.save(entry);
            return;
        }

        User user = userRepository.findById(entry.getUserId()).orElse(null);
        if (user == null) {
            entry.setStatus("failed");
            entry.setLastError("User not found");
            outboxRepository.save(entry);
            markDeliveryFailed(delivery, "User not found");
            return;
        }

        NotificationChannel channel = channels.stream()
                .filter(c -> c.getChannelName().equals(entry.getChannel()))
                .findFirst()
                .orElse(null);

        if (channel == null) {
            entry.setStatus("failed");
            entry.setLastError("Unknown channel: " + entry.getChannel());
            outboxRepository.save(entry);
            markDeliveryFailed(delivery, "Unknown channel");
            return;
        }

        // Send via provider
        channel.send(user, entry.getTitle(), entry.getMessage());

        // Provider accepted → mark delivery SENT
        delivery.setStatus("sent");
        delivery.setSentAt(Instant.now());
        deliveryRepository.save(delivery);

        // Notification record is a separate projection (non-blocking, can fail)
        try {
            Notification notification = new Notification();
            notification.setUserId(user.getId());
            notification.setEventId(entry.getEventId());
            notification.setTitle(entry.getTitle());
            notification.setMessage(entry.getMessage());
            notification.setChannel(entry.getChannel());
            notificationRepository.save(notification);
        } catch (Exception e) {
            // Non-fatal: notification record is a projection, delivery already succeeded
            log.warn("Failed to create notification record (delivery succeeded): {}", e.getMessage());
        }

        // Mark outbox sent
        entry.setStatus("sent");
        outboxRepository.save(entry);

        log.debug("Delivered outbox {} via {} → delivery {} SENT", entry.getId(), entry.getChannel(), delivery.getId());
    }

    private void handleFailure(NotificationOutbox entry, String error) {
        entry.setLastError(error);
        if (entry.getAttemptCount() >= entry.getMaxAttempts()) {
            entry.setStatus("failed");
            outboxRepository.save(entry);

            // Mark delivery as failed
            if (entry.getDeliveryId() != null) {
                deliveryRepository.findById(entry.getDeliveryId())
                        .ifPresent(d -> markDeliveryFailed(d, error));
            }
        } else {
            // Exponential backoff: 30s, 2min, 10min
            long backoffSeconds = switch (entry.getAttemptCount()) {
                case 1 -> 30;
                case 2 -> 120;
                default -> 600;
            };
            entry.setNextRetryAt(Instant.now().plus(backoffSeconds, ChronoUnit.SECONDS));
            entry.setStatus("pending"); // Will be retried on next cycle
            outboxRepository.save(entry);
        }
    }

    private void markDeliveryFailed(ReminderDelivery delivery, String error) {
        delivery.setStatus("failed");
        delivery.setLastError(error);
        deliveryRepository.save(delivery);
    }
}
