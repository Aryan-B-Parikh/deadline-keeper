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

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OutboxConcurrencyTest {

    @Mock private NotificationOutboxRepository outboxRepository;
    @Mock private ReminderDeliveryRepository deliveryRepository;
    @Mock private UserRepository userRepository;
    @Mock private NotificationChannel emailChannel;

    private NotificationOutboxWriter writer;

    @BeforeEach
    void setUp() {
        when(emailChannel.getChannelName()).thenReturn("email");
        writer = new NotificationOutboxWriter(outboxRepository, deliveryRepository, new OutboxRetryPolicy(30, 600));
    }

    @Test
    @DisplayName("5 concurrent workers -> 100 jobs processed with 0 double claims")
    void concurrentWorkersZeroDoubleClaims() throws Exception {
        int totalJobs = 100;
        int workerCount = 5;

        // Simulated in-memory queue partitioned atomically by claimPendingJobIds
        ConcurrentLinkedQueue<UUID> pendingQueue = new ConcurrentLinkedQueue<>();
        Map<UUID, NotificationOutbox> jobMap = new ConcurrentHashMap<>();
        User user = new User();
        UUID userId = UUID.randomUUID();
        user.setId(userId);
        user.setEmail("test@example.com");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        for (int i = 0; i < totalJobs; i++) {
            UUID id = UUID.randomUUID();
            UUID deliveryId = UUID.randomUUID();
            pendingQueue.add(id);

            NotificationOutbox o = new NotificationOutbox();
            o.setId(id);
            o.setUserId(userId);
            o.setDeliveryId(deliveryId);
            o.setChannel("email");
            o.setStatus("processing");
            o.setAttemptCount(1);
            o.setMaxAttempts(3);
            o.setIdempotencyKey("reminder:" + deliveryId);
            jobMap.put(id, o);

            ReminderDelivery d = new ReminderDelivery();
            d.setId(deliveryId);
            d.setStatus("pending");
            d.setChannel("email");
            when(deliveryRepository.findById(deliveryId)).thenReturn(Optional.of(d));
        }

        // Mock claimPendingJobIds to atomically poll from the queue in batches
        when(outboxRepository.claimPendingJobIds(anyInt(), anyLong())).thenAnswer(invocation -> {
            int limit = invocation.getArgument(0);
            List<UUID> claimed = new ArrayList<>();
            for (int i = 0; i < limit; i++) {
                UUID id = pendingQueue.poll();
                if (id == null) break;
                claimed.add(id);
            }
            return claimed;
        });

        when(outboxRepository.findAllById(any())).thenAnswer(invocation -> {
            Iterable<UUID> ids = invocation.getArgument(0);
            List<NotificationOutbox> list = new ArrayList<>();
            for (UUID id : ids) {
                if (jobMap.containsKey(id)) {
                    list.add(jobMap.get(id));
                }
            }
            return list;
        });

        when(outboxRepository.markSentIfOwned(any())).thenReturn(1);

        AtomicInteger totalSends = new AtomicInteger(0);
        Set<String> processedKeys = ConcurrentHashMap.newKeySet();

        doAnswer(invocation -> {
            String key = invocation.getArgument(3);
            boolean isNew = processedKeys.add(key);
            if (!isNew) {
                throw new IllegalStateException("Duplicate send detected for key: " + key);
            }
            totalSends.incrementAndGet();
            return null;
        }).when(emailChannel).send(any(), any(), any(), any());

        ExecutorService executor = Executors.newFixedThreadPool(workerCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(workerCount);

        for (int w = 0; w < workerCount; w++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    NotificationOutboxProcessor workerProcessor = new NotificationOutboxProcessor(
                            outboxRepository, writer, deliveryRepository, userRepository, List.of(emailChannel), new OutboxRetryPolicy(30, 600), 120, 50);

                    while (!pendingQueue.isEmpty()) {
                        workerProcessor.processPending();
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(totalSends.get()).isEqualTo(totalJobs);
        assertThat(processedKeys.size()).isEqualTo(totalJobs);
    }
}
