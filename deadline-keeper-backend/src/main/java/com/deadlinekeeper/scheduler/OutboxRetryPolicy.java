package com.deadlinekeeper.scheduler;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Centralized retry and exponential backoff policy for outbox notification jobs.
 * Formula: delay = min(retryMaxSeconds, retryBaseSeconds * 2^(attemptCount - 1))
 */
@Component
public class OutboxRetryPolicy {

    private final long retryBaseSeconds;
    private final long retryMaxSeconds;

    public OutboxRetryPolicy(
            @Value("${outbox.retry-base-seconds:30}") long retryBaseSeconds,
            @Value("${outbox.retry-max-seconds:600}") long retryMaxSeconds) {
        if (retryBaseSeconds <= 0) {
            throw new IllegalArgumentException("outbox.retry-base-seconds must be > 0");
        }
        if (retryMaxSeconds < retryBaseSeconds) {
            throw new IllegalArgumentException("outbox.retry-max-seconds must be >= retryBaseSeconds");
        }
        this.retryBaseSeconds = retryBaseSeconds;
        this.retryMaxSeconds = retryMaxSeconds;
    }

    /**
     * Calculate the backoff duration in seconds for a given attempt count.
     * Attempt 1 -> retryBaseSeconds
     * Attempt 2 -> retryBaseSeconds * 2
     * Attempt 3 -> retryBaseSeconds * 4
     * ...capped at retryMaxSeconds.
     */
    public long calculateBackoffSeconds(int attemptCount) {
        if (attemptCount <= 1) {
            return retryBaseSeconds;
        }
        // Protect against integer overflow when shifting
        int shift = Math.min(attemptCount - 1, 30);
        long multiplier = 1L << shift;
        long calculated = retryBaseSeconds * multiplier;
        if (calculated < 0 || calculated > retryMaxSeconds) {
            return retryMaxSeconds;
        }
        return Math.min(calculated, retryMaxSeconds);
    }

    /**
     * Calculate the next retry timestamp based on current time + exponential backoff.
     */
    public Instant calculateNextRetry(int attemptCount) {
        long seconds = calculateBackoffSeconds(attemptCount);
        return Instant.now().plus(Duration.ofSeconds(seconds));
    }

    public long getRetryBaseSeconds() {
        return retryBaseSeconds;
    }

    public long getRetryMaxSeconds() {
        return retryMaxSeconds;
    }
}
