package com.deadlinekeeper.scheduler;

import com.deadlinekeeper.model.NotificationOutbox;
import com.deadlinekeeper.model.ReminderDelivery;
import com.deadlinekeeper.repository.NotificationOutboxRepository;
import com.deadlinekeeper.repository.ReminderDeliveryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class NotificationOutboxWriter {

    private static final Logger log = LoggerFactory.getLogger(NotificationOutboxWriter.class);

    private final NotificationOutboxRepository outboxRepository;
    private final ReminderDeliveryRepository deliveryRepository;
    private final OutboxRetryPolicy retryPolicy;

    public NotificationOutboxWriter(NotificationOutboxRepository outboxRepository,
                                    ReminderDeliveryRepository deliveryRepository,
                                    OutboxRetryPolicy retryPolicy) {
        this.outboxRepository = outboxRepository;
        this.deliveryRepository = deliveryRepository;
        this.retryPolicy = retryPolicy;
    }

    @Transactional
    public void markSent(NotificationOutbox entry) {
        int updated = outboxRepository.markSentIfOwned(entry.getId());
        if (updated == 0) {
            log.warn("Lost ownership of outbox {} (lease expired or reclaimed) - skipping markSent", entry.getId());
            return;
        }

        if (entry.getDeliveryId() != null) {
            deliveryRepository.findById(entry.getDeliveryId()).ifPresent(delivery -> {
                delivery.setStatus("sent");
                delivery.setSentAt(Instant.now());
                deliveryRepository.save(delivery);
            });
        }
        log.debug("Delivered outbox {} via {} -> delivery {} SENT",
                entry.getId(), entry.getChannel(), entry.getDeliveryId());
    }

    @Transactional
    public void handleProviderFailure(NotificationOutbox entry, String error) {
        NotificationOutbox outbox = outboxRepository.findById(entry.getId()).orElse(null);
        if (outbox == null) return;

        if (outbox.getAttemptCount() >= outbox.getMaxAttempts()) {
            int updated = outboxRepository.markFailedIfOwned(entry.getId(), error);
            if (updated > 0) {
                markDeliveryFailed(outbox.getDeliveryId(), error);
                log.info("Outbox {} reached max attempts -> FAILED", entry.getId());
            } else {
                log.warn("Lost ownership of outbox {} - skipping markFailed", entry.getId());
            }
        } else {
            Instant nextRetry = retryPolicy.calculateNextRetry(outbox.getAttemptCount());
            int updated = outboxRepository.markRetryIfOwned(entry.getId(), nextRetry, error);
            if (updated == 0) {
                log.warn("Lost ownership of outbox {} - skipping markRetry", entry.getId());
            } else {
                log.debug("Outbox {} scheduled for retry at {}", entry.getId(), nextRetry);
            }
        }
    }

    @Transactional
    public void failPermanently(NotificationOutbox entry, String error) {
        int updated = outboxRepository.markFailedIfOwned(entry.getId(), error);
        if (updated > 0) {
            markDeliveryFailed(entry.getDeliveryId(), error);
            log.info("Outbox {} permanently failed: {}", entry.getId(), error);
        } else {
            log.warn("Lost ownership of outbox {} - skipping failPermanently", entry.getId());
        }
    }

    private void markDeliveryFailed(UUID deliveryId, String error) {
        if (deliveryId == null) return;
        deliveryRepository.findById(deliveryId).ifPresent(d -> {
            d.setStatus("failed");
            d.setLastError(error);
            deliveryRepository.save(d);
        });
    }
}

