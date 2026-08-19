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

    public NotificationOutboxWriter(NotificationOutboxRepository outboxRepository,
                                    ReminderDeliveryRepository deliveryRepository) {
        this.outboxRepository = outboxRepository;
        this.deliveryRepository = deliveryRepository;
    }

    @Transactional
    public void markSent(NotificationOutbox entry) {
        NotificationOutbox outbox = outboxRepository.findById(entry.getId()).orElse(null);
        if (outbox == null || !"processing".equals(outbox.getStatus())) return;

        outbox.setStatus("sent");
        outbox.setLeaseUntil(null);
        outboxRepository.save(outbox);

        if (outbox.getDeliveryId() != null) {
            deliveryRepository.findById(outbox.getDeliveryId()).ifPresent(delivery -> {
                delivery.setStatus("sent");
                delivery.setSentAt(Instant.now());
                deliveryRepository.save(delivery);
            });
        }
        log.debug("Delivered outbox {} via {} -> delivery {} SENT",
                outbox.getId(), outbox.getChannel(), outbox.getDeliveryId());
    }

    @Transactional
    public void handleProviderFailure(NotificationOutbox entry, String error) {
        NotificationOutbox outbox = outboxRepository.findById(entry.getId()).orElse(null);
        if (outbox == null) return;

        outbox.setLastError(error);
        outbox.setLeaseUntil(null);
        outbox.setProcessingStartedAt(null);

        if (outbox.getAttemptCount() >= outbox.getMaxAttempts()) {
            outbox.setStatus("failed");
            outboxRepository.save(outbox);
            markDeliveryFailed(outbox.getDeliveryId(), error);
        } else {
            long backoffSeconds = switch (outbox.getAttemptCount()) {
                case 1 -> 30;
                case 2 -> 120;
                default -> 600;
            };
            outbox.setNextRetryAt(Instant.now().plusSeconds(backoffSeconds));
            outbox.setStatus("pending");
            outboxRepository.save(outbox);
        }
    }

    @Transactional
    public void failPermanently(NotificationOutbox entry, String error) {
        NotificationOutbox outbox = outboxRepository.findById(entry.getId()).orElse(null);
        if (outbox == null) return;

        outbox.setStatus("failed");
        outbox.setLastError(error);
        outbox.setLeaseUntil(null);
        outboxRepository.save(outbox);
        markDeliveryFailed(outbox.getDeliveryId(), error);
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
