package com.deadlinekeeper.scheduler;

import com.deadlinekeeper.model.NotificationOutbox;
import com.deadlinekeeper.model.ReminderDelivery;
import com.deadlinekeeper.model.User;
import com.deadlinekeeper.notification.NotificationChannel;
import com.deadlinekeeper.repository.NotificationOutboxRepository;
import com.deadlinekeeper.repository.ReminderDeliveryRepository;
import com.deadlinekeeper.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OutboxFailureMatrixTest {

    @Mock private NotificationOutboxRepository outboxRepository;
    @Mock private ReminderDeliveryRepository deliveryRepository;
    @Mock private UserRepository userRepository;
    @Mock private NotificationChannel emailChannel;

    private NotificationOutboxWriter writer;
    private NotificationOutboxProcessor processor;

    private final UUID userId = UUID.randomUUID();
    private final UUID eventId = UUID.randomUUID();
    private final UUID deliveryId = UUID.randomUUID();
    private final UUID outboxId = UUID.randomUUID();
    private User user;
    private ReminderDelivery delivery;
    private NotificationOutbox outbox;

    @BeforeEach
    void setUp() {
        when(emailChannel.getChannelName()).thenReturn("email");

        OutboxRetryPolicy retryPolicy = new OutboxRetryPolicy(30, 600);
        writer = new NotificationOutboxWriter(outboxRepository, deliveryRepository, retryPolicy);
        processor = new NotificationOutboxProcessor(
                outboxRepository, writer, deliveryRepository, userRepository, List.of(emailChannel), retryPolicy, 120, 50);

        user = new User();
        user.setId(userId);
        user.setEmail("user@example.com");

        delivery = new ReminderDelivery();
        delivery.setId(deliveryId);
        delivery.setEventId(eventId);
        delivery.setChannel("email");
        delivery.setStatus("pending");

        outbox = new NotificationOutbox();
        outbox.setId(outboxId);
        outbox.setUserId(userId);
        outbox.setEventId(eventId);
        outbox.setDeliveryId(deliveryId);
        outbox.setChannel("email");
        outbox.setStatus("processing");
        outbox.setAttemptCount(1);
        outbox.setMaxAttempts(3);
        outbox.setIdempotencyKey("reminder:" + deliveryId);
    }

    @Test
    @DisplayName("Provider timeout -> backoff retry scheduled")
    void providerTimeoutSchedulesRetry() {
        when(outboxRepository.claimPendingJobIds(50, 120L)).thenReturn(List.of(outboxId));
        when(outboxRepository.findAllById(List.of(outboxId))).thenReturn(List.of(outbox));
        when(deliveryRepository.findById(deliveryId)).thenReturn(Optional.of(delivery));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(outboxRepository.findById(outboxId)).thenReturn(Optional.of(outbox));
        when(outboxRepository.markRetryIfOwned(eq(outboxId), any(Instant.class), anyString())).thenReturn(1);

        doThrow(new RuntimeException("Connection timed out"))
                .when(emailChannel).send(any(), any(), any(), any(), any());

        processor.processPending();

        verify(outboxRepository).markRetryIfOwned(eq(outboxId), any(Instant.class), contains("timed out"));
        verify(deliveryRepository, never()).save(argThat(d -> "failed".equals(d.getStatus())));
    }

    @Test
    @DisplayName("Max attempts reached -> marked permanently failed")
    void maxAttemptsReachedFailsPermanently() {
        outbox.setAttemptCount(3);
        when(outboxRepository.claimPendingJobIds(50, 120L)).thenReturn(List.of(outboxId));
        when(outboxRepository.findAllById(List.of(outboxId))).thenReturn(List.of(outbox));
        when(deliveryRepository.findById(deliveryId)).thenReturn(Optional.of(delivery));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(outboxRepository.findById(outboxId)).thenReturn(Optional.of(outbox));
        when(outboxRepository.markFailedIfOwned(eq(outboxId), anyString())).thenReturn(1);

        doThrow(new RuntimeException("500 Internal Server Error"))
                .when(emailChannel).send(any(), any(), any(), any(), any());

        processor.processPending();

        verify(outboxRepository).markFailedIfOwned(eq(outboxId), contains("500 Internal Server Error"));
        verify(deliveryRepository).save(argThat(d -> "failed".equals(d.getStatus())));
    }

    @Test
    @DisplayName("Worker crashes during send -> watchdog reclaims expired lease and fails max attempts with delivery sync")
    void watchdogReclaimsExpiredLease() {
        when(outboxRepository.findExpiredDeliveryIdsExceedingMaxAttempts()).thenReturn(List.of(deliveryId));
        when(outboxRepository.failExpiredLeasesExceedingMaxAttempts()).thenReturn(2);
        when(outboxRepository.reclaimExpiredLeasesWithExponentialBackoff(30, 600)).thenReturn(3);

        int totalReclaimed = processor.reclaimExpiredLeases();

        verify(outboxRepository).findExpiredDeliveryIdsExceedingMaxAttempts();
        verify(deliveryRepository).markDeliveriesFailed(eq(List.of(deliveryId)), contains("Watchdog timeout"));
        verify(outboxRepository).failExpiredLeasesExceedingMaxAttempts();
        verify(outboxRepository).reclaimExpiredLeasesWithExponentialBackoff(30, 600);
        assertThat(totalReclaimed).isEqualTo(5);
    }

    @Test
    @DisplayName("Late worker attempt on reclaimed job fails conditionally without corrupting state")
    void lateWorkerDoesNotCorruptReclaimedJob() {
        when(outboxRepository.markSentIfOwned(outboxId)).thenReturn(0);

        writer.markSent(outbox);

        verify(deliveryRepository, never()).save(any());
    }
}
