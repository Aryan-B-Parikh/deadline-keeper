package com.deadlinekeeper.service;

import com.deadlinekeeper.model.NotificationOutbox;
import com.deadlinekeeper.repository.NotificationOutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class NotificationOutboxService {

    private static final Logger log = LoggerFactory.getLogger(NotificationOutboxService.class);

    private final NotificationOutboxRepository outboxRepository;

    public NotificationOutboxService(NotificationOutboxRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    @Transactional
    public void enqueue(UUID deliveryId, UUID userId, UUID eventId, String title, String message, String channel) {
        // Deterministic idempotency key: same reminder → same key → database enforces uniqueness
        String idempotencyKey = "reminder:%s:%s".formatted(eventId, channel);

        // Check if this exact reminder was already enqueued
        Optional<NotificationOutbox> existing = outboxRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            log.debug("Outbox entry already exists for {}", idempotencyKey);
            return;
        }

        NotificationOutbox outbox = new NotificationOutbox();
        outbox.setUserId(userId);
        outbox.setEventId(eventId);
        outbox.setTitle(title);
        outbox.setMessage(message);
        outbox.setChannel(channel);
        outbox.setIdempotencyKey(idempotencyKey);
        outbox.setStatus("pending");
        outbox.setAttemptCount(0);
        outbox.setMaxAttempts(3);
        outbox.setScheduledAt(Instant.now());
        outboxRepository.save(outbox);
    }
}
