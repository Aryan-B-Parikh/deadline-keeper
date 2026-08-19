package com.deadlinekeeper.scheduler;

import com.deadlinekeeper.model.NotificationOutbox;
import com.deadlinekeeper.model.ReminderDelivery;
import com.deadlinekeeper.model.User;
import com.deadlinekeeper.notification.NotificationChannel;
import com.deadlinekeeper.repository.NotificationOutboxRepository;
import com.deadlinekeeper.repository.ReminderDeliveryRepository;
import com.deadlinekeeper.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class NotificationOutboxProcessor {

    private static final Logger log = LoggerFactory.getLogger(NotificationOutboxProcessor.class);

    static final long LEASE_SECONDS = 120;

    private final NotificationOutboxRepository outboxRepository;
    private final NotificationOutboxWriter writer;
    private final ReminderDeliveryRepository deliveryRepository;
    private final UserRepository userRepository;
    private final List<NotificationChannel> channels;

    public NotificationOutboxProcessor(NotificationOutboxRepository outboxRepository,
                                       NotificationOutboxWriter writer,
                                       ReminderDeliveryRepository deliveryRepository,
                                       UserRepository userRepository,
                                       List<NotificationChannel> channels) {
        this.outboxRepository = outboxRepository;
        this.writer = writer;
        this.deliveryRepository = deliveryRepository;
        this.userRepository = userRepository;
        this.channels = channels;
    }

    public void processPending() {
        List<NotificationOutbox> claimed = claimJobs();
        if (claimed.isEmpty()) return;
        log.debug("Claimed {} outbox jobs", claimed.size());

        for (NotificationOutbox entry : claimed) {
            try {
                sendViaProvider(entry);
            } catch (Exception e) {
                log.error("Provider call failed for outbox {}: {}", entry.getId(), e.getMessage(), e);
                writer.handleProviderFailure(entry, e.getMessage());
            }
        }
    }

    @Transactional
    protected List<NotificationOutbox> claimJobs() {
        int claimed = outboxRepository.claimPendingJobs(50, LEASE_SECONDS);
        if (claimed == 0) return List.of();
        return outboxRepository.findByStatusOrderByScheduledAtAsc("processing");
    }

    protected void sendViaProvider(NotificationOutbox entry) {
        if (entry.getDeliveryId() == null) {
            writer.failPermanently(entry, "No deliveryId");
            return;
        }

        ReminderDelivery delivery = deliveryRepository.findById(entry.getDeliveryId()).orElse(null);
        if (delivery == null) {
            writer.failPermanently(entry, "Delivery not found");
            return;
        }

        if ("sent".equals(delivery.getStatus()) || "failed".equals(delivery.getStatus())) {
            writer.failPermanently(entry, "Delivery already " + delivery.getStatus());
            return;
        }

        User user = userRepository.findById(entry.getUserId()).orElse(null);
        if (user == null) {
            writer.failPermanently(entry, "User not found");
            return;
        }

        NotificationChannel channel = channels.stream()
                .filter(c -> c.getChannelName().equals(entry.getChannel()))
                .findFirst()
                .orElse(null);

        if (channel == null) {
            writer.failPermanently(entry, "Unknown channel: " + entry.getChannel());
            return;
        }

        channel.send(user, entry.getTitle(), entry.getMessage(), entry.getIdempotencyKey());
        writer.markSent(entry);
    }

    @Transactional
    public int reclaimExpiredLeases() {
        return outboxRepository.reclaimExpiredLeases();
    }
}
