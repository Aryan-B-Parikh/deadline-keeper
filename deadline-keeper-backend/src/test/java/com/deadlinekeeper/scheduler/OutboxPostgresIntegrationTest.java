package com.deadlinekeeper.scheduler;

import com.deadlinekeeper.model.Event;
import com.deadlinekeeper.model.NotificationOutbox;
import com.deadlinekeeper.model.Reminder;
import com.deadlinekeeper.model.ReminderDelivery;
import com.deadlinekeeper.model.User;
import com.deadlinekeeper.notification.NotificationChannel;
import com.deadlinekeeper.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@org.junit.jupiter.api.condition.EnabledIf("isDockerAvailable")
@TestPropertySource(properties = {
        "spring.task.scheduling.pool.size=0",
        "outbox.lease-seconds=120",
        "outbox.claim-limit=50",
        "outbox.retry-base-seconds=30"
})
class OutboxPostgresIntegrationTest {

    static boolean isDockerAvailable() {
        try {
            return org.testcontainers.DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            return false;
        }
    }

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("deadline_keeper_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired private NotificationOutboxRepository outboxRepository;
    @Autowired private ReminderDeliveryRepository deliveryRepository;
    @Autowired private EventRepository eventRepository;
    @Autowired private ReminderRepository reminderRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private NotificationOutboxWriter writer;

    @MockBean private NotificationChannel emailChannel;

    private NotificationOutboxProcessor processor;
    private User testUser;
    private Event testEvent;

    @BeforeEach
    void setUp() {
        when(emailChannel.getChannelName()).thenReturn("email");

        processor = new NotificationOutboxProcessor(
                outboxRepository, writer, deliveryRepository, userRepository,
                List.of(emailChannel), new OutboxRetryPolicy(30, 600), 120, 50);

        outboxRepository.deleteAll();
        deliveryRepository.deleteAll();
        reminderRepository.deleteAll();
        eventRepository.deleteAll();
        userRepository.deleteAll();

        testUser = new User();
        testUser.setId(UUID.randomUUID());
        testUser.setEmail("integration@example.com");
        testUser.setDisplayName("Integration User");
        testUser.setForwardingToken(UUID.randomUUID().toString().replace("-", ""));
        testUser = userRepository.save(testUser);

        testEvent = new Event();
        testEvent.setUserId(testUser.getId());
        testEvent.setTitle("Final Project Submission");
        testEvent.setType("assignment");
        testEvent.setDueAt(Instant.now().plusSeconds(86400));
        testEvent.setTimezone("UTC");
        testEvent.setSource("manual");
        testEvent = eventRepository.save(testEvent);
    }

    @Test
    @DisplayName("PostgreSQL atomic claim: 5 concurrent workers process 100 jobs with zero double claims")
    void concurrentPostgresWorkersZeroDoubleClaims() throws Exception {
        int totalJobs = 100;
        int workerCount = 5;

        // Seed 100 distinct reminders and deliveries to honor UNIQUE(event_id, reminder_id, channel)
        for (int i = 0; i < totalJobs; i++) {
            Reminder reminder = new Reminder();
            reminder.setEventId(testEvent.getId());
            reminder.setOffsetSeconds((long) (i + 1) * 60);
            reminder.setChannel("email");
            reminder = reminderRepository.save(reminder);

            ReminderDelivery delivery = new ReminderDelivery();
            delivery.setEventId(testEvent.getId());
            delivery.setReminderId(reminder.getId());
            delivery.setChannel("email");
            delivery.setStatus("pending");
            delivery.setScheduledAt(Instant.now().minusSeconds(10));
            delivery = deliveryRepository.save(delivery);

            NotificationOutbox outbox = new NotificationOutbox();
            outbox.setUserId(testUser.getId());
            outbox.setEventId(testEvent.getId());
            outbox.setDeliveryId(delivery.getId());
            outbox.setTitle("Reminder " + i);
            outbox.setMessage("Due soon");
            outbox.setChannel("email");
            outbox.setStatus("pending");
            outbox.setAttemptCount(0);
            outbox.setMaxAttempts(3);
            outbox.setScheduledAt(Instant.now().minusSeconds(10));
            outbox.setIdempotencyKey("reminder:" + delivery.getId());
            outboxRepository.save(outbox);
        }

        AtomicInteger totalSends = new AtomicInteger(0);
        ConcurrentHashMap<String, AtomicInteger> activeClaims = new ConcurrentHashMap<>();
        ConcurrentHashMap<String, Integer> maxActiveClaims = new ConcurrentHashMap<>();

        doAnswer(invocation -> {
            String key = invocation.getArgument(3);
            AtomicInteger claims = activeClaims.computeIfAbsent(key, k -> new AtomicInteger(0));
            int current = claims.incrementAndGet();
            maxActiveClaims.merge(key, current, Math::max);
            
            try {
                // Simulate some work to increase the chance of overlap if a concurrency bug exists
                Thread.sleep(5);
            } finally {
                claims.decrementAndGet();
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
                    while (outboxRepository.countByStatus("pending") > 0) {
                        processor.processPending();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean finished = doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finished).isTrue();
        assertThat(totalSends.get()).isGreaterThanOrEqualTo(totalJobs);
        assertThat(outboxRepository.countByStatus("sent")).isEqualTo(totalJobs);
        assertThat(outboxRepository.countByStatus("pending")).isEqualTo(0);

        assertThat(maxActiveClaims.size()).isEqualTo(totalJobs);
        for (Map.Entry<String, Integer> entry : maxActiveClaims.entrySet()) {
            assertThat(entry.getValue())
                .as("Job %s should have a max of 1 simultaneous owner", entry.getKey())
                .isEqualTo(1);
        }
    }

    @Test
    @DisplayName("Watchdog reclaims expired lease and advances retry with backoff in PostgreSQL")
    void watchdogReclaimsInPostgres() {
        Reminder reminder = new Reminder();
        reminder.setEventId(testEvent.getId());
        reminder.setOffsetSeconds(3600L);
        reminder.setChannel("email");
        reminder = reminderRepository.save(reminder);

        ReminderDelivery delivery = new ReminderDelivery();
        delivery.setEventId(testEvent.getId());
        delivery.setReminderId(reminder.getId());
        delivery.setChannel("email");
        delivery.setStatus("processing");
        delivery = deliveryRepository.save(delivery);

        NotificationOutbox outbox = new NotificationOutbox();
        outbox.setUserId(testUser.getId());
        outbox.setEventId(testEvent.getId());
        outbox.setDeliveryId(delivery.getId());
        outbox.setTitle("Expired Job");
        outbox.setMessage("Message");
        outbox.setChannel("email");
        outbox.setStatus("processing");
        outbox.setAttemptCount(1);
        outbox.setMaxAttempts(3);
        outbox.setLeaseUntil(Instant.now().minusSeconds(10)); // expired lease
        outbox.setIdempotencyKey("reminder:" + delivery.getId());
        outbox = outboxRepository.save(outbox);

        int reclaimed = processor.reclaimExpiredLeases();

        assertThat(reclaimed).isEqualTo(1);
        NotificationOutbox refreshed = outboxRepository.findById(outbox.getId()).orElseThrow();
        assertThat(refreshed.getStatus()).isEqualTo("pending");
        assertThat(refreshed.getLastError()).contains("Lease expired");
        assertThat(refreshed.getNextRetryAt()).isNotNull();
    }

    @Test
    @DisplayName("Expired lease exceeding max attempts transitions outbox AND delivery to failed")
    void expiredLeaseExceedingMaxAttemptsFailsBoth() {
        Reminder reminder = new Reminder();
        reminder.setEventId(testEvent.getId());
        reminder.setOffsetSeconds(3600L);
        reminder.setChannel("email");
        reminder = reminderRepository.save(reminder);

        ReminderDelivery delivery = new ReminderDelivery();
        delivery.setEventId(testEvent.getId());
        delivery.setReminderId(reminder.getId());
        delivery.setChannel("email");
        delivery.setStatus("processing");
        delivery = deliveryRepository.save(delivery);

        NotificationOutbox outbox = new NotificationOutbox();
        outbox.setUserId(testUser.getId());
        outbox.setEventId(testEvent.getId());
        outbox.setDeliveryId(delivery.getId());
        outbox.setTitle("Permanently Failed Job");
        outbox.setMessage("Message");
        outbox.setChannel("email");
        outbox.setStatus("processing");
        outbox.setAttemptCount(3);
        outbox.setMaxAttempts(3);
        outbox.setLeaseUntil(Instant.now().minusSeconds(10)); // expired lease
        outbox.setIdempotencyKey("reminder:" + delivery.getId());
        outbox = outboxRepository.save(outbox);

        int count = processor.reclaimExpiredLeases();

        assertThat(count).isEqualTo(1);
        NotificationOutbox refreshedOutbox = outboxRepository.findById(outbox.getId()).orElseThrow();
        assertThat(refreshedOutbox.getStatus()).isEqualTo("failed");
        assertThat(refreshedOutbox.getLastError()).contains("max attempts exceeded");

        ReminderDelivery refreshedDelivery = deliveryRepository.findById(delivery.getId()).orElseThrow();
        assertThat(refreshedDelivery.getStatus()).isEqualTo("failed");
        assertThat(refreshedDelivery.getLastError()).contains("Watchdog timeout");
    }

    @Test
    @DisplayName("Active lease is not reclaimed by watchdog")
    void activeLeaseNotReclaimed() {
        Reminder reminder = new Reminder();
        reminder.setEventId(testEvent.getId());
        reminder.setOffsetSeconds(3600L);
        reminder.setChannel("email");
        reminder = reminderRepository.save(reminder);

        ReminderDelivery delivery = new ReminderDelivery();
        delivery.setEventId(testEvent.getId());
        delivery.setReminderId(reminder.getId());
        delivery.setChannel("email");
        delivery.setStatus("processing");
        delivery = deliveryRepository.save(delivery);

        NotificationOutbox outbox = new NotificationOutbox();
        outbox.setUserId(testUser.getId());
        outbox.setEventId(testEvent.getId());
        outbox.setDeliveryId(delivery.getId());
        outbox.setTitle("Active Lease Job");
        outbox.setMessage("Message");
        outbox.setChannel("email");
        outbox.setStatus("processing");
        outbox.setAttemptCount(1);
        outbox.setMaxAttempts(3);
        outbox.setLeaseUntil(Instant.now().plusSeconds(120)); // ACTIVE lease
        outbox.setIdempotencyKey("reminder:" + delivery.getId());
        outbox = outboxRepository.save(outbox);

        int count = processor.reclaimExpiredLeases();

        assertThat(count).isEqualTo(0);
        NotificationOutbox refreshedOutbox = outboxRepository.findById(outbox.getId()).orElseThrow();
        assertThat(refreshedOutbox.getStatus()).isEqualTo("processing");
    }

    @Test
    @DisplayName("Stale worker cannot mutate state after losing ownership in PostgreSQL")
    void staleWorkerCannotMutateState() {
        Reminder reminder = new Reminder();
        reminder.setEventId(testEvent.getId());
        reminder.setOffsetSeconds(3600L);
        reminder.setChannel("email");
        reminder = reminderRepository.save(reminder);

        ReminderDelivery delivery = new ReminderDelivery();
        delivery.setEventId(testEvent.getId());
        delivery.setReminderId(reminder.getId());
        delivery.setChannel("email");
        delivery.setStatus("processing");
        delivery = deliveryRepository.save(delivery);

        NotificationOutbox outbox = new NotificationOutbox();
        outbox.setUserId(testUser.getId());
        outbox.setEventId(testEvent.getId());
        outbox.setDeliveryId(delivery.getId());
        outbox.setTitle("Stale Job");
        outbox.setMessage("Message");
        outbox.setChannel("email");
        outbox.setStatus("processing");
        outbox.setAttemptCount(1);
        outbox.setMaxAttempts(3);
        outbox.setLeaseUntil(Instant.now().minusSeconds(10)); // expired
        outbox.setIdempotencyKey("reminder:" + delivery.getId());
        outbox = outboxRepository.save(outbox);

        // Stale worker tries to markSent on expired outbox row
        int updated = outboxRepository.markSentIfOwned(outbox.getId());
        assertThat(updated).isEqualTo(0);

        writer.markSent(outbox);

        // Verify delivery status was not altered to sent
        ReminderDelivery refreshedDelivery = deliveryRepository.findById(delivery.getId()).orElseThrow();
        assertThat(refreshedDelivery.getStatus()).isEqualTo("processing");
    }
}
