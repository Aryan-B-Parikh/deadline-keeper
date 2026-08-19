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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationOutboxProcessorTest {

    @Mock private NotificationOutboxRepository outboxRepository;
    @Mock private NotificationOutboxWriter writer;
    @Mock private ReminderDeliveryRepository deliveryRepository;
    @Mock private UserRepository userRepository;

    private NotificationOutboxProcessor processor;
    private NotificationChannel mockChannel;

    private UUID userId = UUID.randomUUID();
    private UUID eventId = UUID.randomUUID();
    private UUID deliveryId = UUID.randomUUID();
    private UUID outboxId = UUID.randomUUID();
    private User user;
    private ReminderDelivery delivery;
    private NotificationOutbox outbox;

    @BeforeEach
    void setUp() {
        mockChannel = mock(NotificationChannel.class);
        when(mockChannel.getChannelName()).thenReturn("email");

        OutboxRetryPolicy retryPolicy = new OutboxRetryPolicy(30, 600);
        processor = new NotificationOutboxProcessor(
                outboxRepository, writer, deliveryRepository, userRepository, List.of(mockChannel), retryPolicy, 120, 50);

        user = new User();
        user.setId(userId);
        user.setEmail("test@example.com");

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
    @DisplayName("Provider succeeds -> markSent called")
    void providerSucceeds() {
        when(outboxRepository.claimPendingJobIds(50, 120L)).thenReturn(List.of(outboxId));
        when(outboxRepository.findAllById(List.of(outboxId))).thenReturn(List.of(outbox));
        when(deliveryRepository.findById(deliveryId)).thenReturn(Optional.of(delivery));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        processor.processPending();

        verify(mockChannel).send(eq(user), any(), any(), eq("reminder:" + deliveryId));
        verify(writer).markSent(outbox);
        verify(writer, never()).handleProviderFailure(any(), any());
    }

    @Test
    @DisplayName("Provider fails -> handleProviderFailure called")
    void providerFails() {
        when(outboxRepository.claimPendingJobIds(50, 120L)).thenReturn(List.of(outboxId));
        when(outboxRepository.findAllById(List.of(outboxId))).thenReturn(List.of(outbox));
        when(deliveryRepository.findById(deliveryId)).thenReturn(Optional.of(delivery));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        doThrow(new RuntimeException("SendGrid timeout"))
                .when(mockChannel).send(any(), any(), any(), any());

        processor.processPending();

        verify(writer).handleProviderFailure(eq(outbox), eq("SendGrid timeout"));
        verify(writer, never()).markSent(any());
    }

    @Test
    @DisplayName("Delivery already terminal -> failPermanently called")
    void deliveryAlreadyTerminal() {
        delivery.setStatus("sent");
        when(deliveryRepository.findById(deliveryId)).thenReturn(Optional.of(delivery));

        processor.sendViaProvider(outbox);

        verify(mockChannel, never()).send(any(), any(), any(), any());
        verify(writer).failPermanently(eq(outbox), eq("Delivery already sent"));
    }

    @Test
    @DisplayName("Missing deliveryId -> failPermanently called")
    void missingDeliveryId() {
        outbox.setDeliveryId(null);

        processor.sendViaProvider(outbox);

        verify(mockChannel, never()).send(any(), any(), any(), any());
        verify(writer).failPermanently(eq(outbox), eq("No deliveryId"));
    }

    @Test
    @DisplayName("Delivery not found -> failPermanently called")
    void deliveryNotFound() {
        when(deliveryRepository.findById(deliveryId)).thenReturn(Optional.empty());

        processor.sendViaProvider(outbox);

        verify(writer).failPermanently(eq(outbox), eq("Delivery not found"));
    }

    @Test
    @DisplayName("User not found -> failPermanently called")
    void userNotFound() {
        when(deliveryRepository.findById(deliveryId)).thenReturn(Optional.of(delivery));
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        processor.sendViaProvider(outbox);

        verify(writer).failPermanently(eq(outbox), eq("User not found"));
    }

    @Test
    @DisplayName("Unknown channel -> failPermanently called")
    void unknownChannel() {
        when(deliveryRepository.findById(deliveryId)).thenReturn(Optional.of(delivery));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        processor = new NotificationOutboxProcessor(
                outboxRepository, writer, deliveryRepository, userRepository, List.of(), new OutboxRetryPolicy(30, 600), 120, 50);

        processor.sendViaProvider(outbox);

        verify(writer).failPermanently(eq(outbox), contains("Unknown channel"));
    }

    @Test
    @DisplayName("No claimed jobs -> no provider calls")
    void noClaimedJobs() {
        when(outboxRepository.claimPendingJobIds(50, 120L)).thenReturn(List.of());

        processor.processPending();

        verify(mockChannel, never()).send(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Multiple reminders same event -> all delivered independently")
    void multipleReminders() {
        UUID dv1 = UUID.randomUUID();
        UUID dv2 = UUID.randomUUID();
        UUID dv3 = UUID.randomUUID();

        ReminderDelivery d1 = new ReminderDelivery();
        d1.setId(dv1); d1.setStatus("pending"); d1.setChannel("email");
        ReminderDelivery d2 = new ReminderDelivery();
        d2.setId(dv2); d2.setStatus("pending"); d2.setChannel("email");
        ReminderDelivery d3 = new ReminderDelivery();
        d3.setId(dv3); d3.setStatus("pending"); d3.setChannel("email");

        NotificationOutbox o1 = makeOutbox(dv1);
        NotificationOutbox o2 = makeOutbox(dv2);
        NotificationOutbox o3 = makeOutbox(dv3);

        List<UUID> claimedIds = List.of(o1.getId(), o2.getId(), o3.getId());
        when(outboxRepository.claimPendingJobIds(50, 120L)).thenReturn(claimedIds);
        when(outboxRepository.findAllById(claimedIds)).thenReturn(List.of(o1, o2, o3));
        when(deliveryRepository.findById(dv1)).thenReturn(Optional.of(d1));
        when(deliveryRepository.findById(dv2)).thenReturn(Optional.of(d2));
        when(deliveryRepository.findById(dv3)).thenReturn(Optional.of(d3));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        processor.processPending();

        verify(mockChannel, times(3)).send(eq(user), any(), any(), any());
        verify(writer, times(3)).markSent(any());
    }

    @Test
    @DisplayName("Watchdog reclaims expired leases")
    void watchdogReclaim() {
        when(outboxRepository.failExpiredLeasesExceedingMaxAttempts()).thenReturn(1);
        when(outboxRepository.reclaimExpiredLeasesWithBackoff(30)).thenReturn(1);

        int reclaimed = processor.reclaimExpiredLeases();

        assertThat(reclaimed).isEqualTo(2);
    }

    @Test
    @DisplayName("Provider receives idempotency key")
    void providerGetsIdempotencyKey() {
        when(outboxRepository.claimPendingJobIds(50, 120L)).thenReturn(List.of(outboxId));
        when(outboxRepository.findAllById(List.of(outboxId))).thenReturn(List.of(outbox));
        when(deliveryRepository.findById(deliveryId)).thenReturn(Optional.of(delivery));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        processor.processPending();

        verify(mockChannel).send(any(), any(), any(), eq("reminder:" + deliveryId));
    }

    @Test
    @DisplayName("Lost ownership during markSent -> delivery not marked sent")
    void lostOwnershipMarkSent() {
        NotificationOutboxWriter realWriter = new NotificationOutboxWriter(outboxRepository, deliveryRepository, new OutboxRetryPolicy(30, 600));
        when(outboxRepository.markSentIfOwned(outboxId)).thenReturn(0);

        realWriter.markSent(outbox);

        verify(deliveryRepository, never()).findById(any());
        verify(deliveryRepository, never()).save(any());
    }

    @Test
    @DisplayName("Held ownership during markSent -> delivery marked sent")
    void heldOwnershipMarkSent() {
        NotificationOutboxWriter realWriter = new NotificationOutboxWriter(outboxRepository, deliveryRepository, new OutboxRetryPolicy(30, 600));
        when(outboxRepository.markSentIfOwned(outboxId)).thenReturn(1);
        when(deliveryRepository.findById(deliveryId)).thenReturn(Optional.of(delivery));

        realWriter.markSent(outbox);

        verify(deliveryRepository).save(argThat(d -> "sent".equals(d.getStatus())));
    }

    private NotificationOutbox makeOutbox(UUID dvId) {
        NotificationOutbox o = new NotificationOutbox();
        o.setId(UUID.randomUUID());
        o.setUserId(userId);
        o.setEventId(eventId);
        o.setDeliveryId(dvId);
        o.setChannel("email");
        o.setStatus("processing");
        o.setAttemptCount(1);
        o.setMaxAttempts(3);
        o.setIdempotencyKey("reminder:" + dvId);
        o.setTitle("Test");
        o.setMessage("Message");
        return o;
    }
}
