package com.deadlinekeeper.scheduler;

import com.deadlinekeeper.model.Notification;
import com.deadlinekeeper.model.NotificationOutbox;
import com.deadlinekeeper.model.User;
import com.deadlinekeeper.notification.NotificationChannel;
import com.deadlinekeeper.repository.NotificationOutboxRepository;
import com.deadlinekeeper.repository.NotificationRepository;
import com.deadlinekeeper.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class NotificationOutboxProcessor {

    private static final Logger log = LoggerFactory.getLogger(NotificationOutboxProcessor.class);

    private final NotificationOutboxRepository outboxRepository;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final List<NotificationChannel> channels;

    public NotificationOutboxProcessor(NotificationOutboxRepository outboxRepository,
                                       NotificationRepository notificationRepository,
                                       UserRepository userRepository,
                                       List<NotificationChannel> channels) {
        this.outboxRepository = outboxRepository;
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.channels = channels;
    }

    @Transactional
    public void processPending() {
        List<NotificationOutbox> pending = outboxRepository.findByStatusInOrderByScheduledAtAsc(
                List.of("pending"), PageRequest.of(0, 50));

        for (NotificationOutbox entry : pending) {
            if (entry.getAttemptCount() >= entry.getMaxAttempts()) {
                entry.setStatus("failed");
                outboxRepository.save(entry);
                continue;
            }

            entry.setStatus("processing");
            entry.setAttemptCount(entry.getAttemptCount() + 1);
            outboxRepository.save(entry);

            try {
                NotificationChannel channel = channels.stream()
                        .filter(c -> c.getChannelName().equals(entry.getChannel()))
                        .findFirst()
                        .orElse(null);

                if (channel == null) {
                    entry.setStatus("failed");
                    entry.setLastError("Unknown channel: " + entry.getChannel());
                    outboxRepository.save(entry);
                    continue;
                }

                User user = userRepository.findById(entry.getUserId()).orElse(null);
                if (user == null) {
                    entry.setStatus("failed");
                    entry.setLastError("User not found");
                    outboxRepository.save(entry);
                    continue;
                }

                channel.send(user, entry.getTitle(), entry.getMessage());

                Notification notification = new Notification();
                notification.setUserId(user.getId());
                notification.setEventId(entry.getEventId());
                notification.setTitle(entry.getTitle());
                notification.setMessage(entry.getMessage());
                notification.setChannel(entry.getChannel());
                notificationRepository.save(notification);

                entry.setStatus("sent");
                outboxRepository.save(entry);

            } catch (Exception e) {
                log.error("Failed to send notification: {}", e.getMessage(), e);
                if (entry.getAttemptCount() >= entry.getMaxAttempts()) {
                    entry.setStatus("failed");
                } else {
                    entry.setStatus("pending");
                }
                entry.setLastError(e.getMessage());
                outboxRepository.save(entry);
            }
        }
    }
}
