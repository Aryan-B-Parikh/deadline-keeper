package com.deadlinekeeper.service;

import com.deadlinekeeper.model.NotificationOutbox;
import com.deadlinekeeper.repository.NotificationOutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class NotificationOutboxService {

    private static final Logger log = LoggerFactory.getLogger(NotificationOutboxService.class);

    private final NotificationOutboxRepository outboxRepository;

    public NotificationOutboxService(NotificationOutboxRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    @Transactional
    public void enqueue(UUID userId, UUID eventId, String title, String message, String channel) {
        String idempotencyKey = "reminder_%s_%s_%s".formatted(eventId, UUID.randomUUID(), Instant.now().toEpochMilli());

        Instant oneHourAgo = Instant.now().minusSeconds(3600);
        List<NotificationOutbox> recent = outboxRepository.findRecentByEventAndChannel(eventId, channel, oneHourAgo);
        if (!recent.isEmpty()) return;

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