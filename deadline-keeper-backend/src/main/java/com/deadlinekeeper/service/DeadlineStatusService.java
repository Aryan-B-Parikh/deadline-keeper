package com.deadlinekeeper.service;

import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class DeadlineStatusService {

    private static final long DUE_SOON_DAYS = 3;

    public String computeStatus(Instant dueAt) {
        if (dueAt == null) return "upcoming";
        Instant now = Instant.now();
        if (dueAt.isBefore(now)) return "overdue";
        if (dueAt.minusSeconds(DUE_SOON_DAYS * 86400).isBefore(now)) return "due_soon";
        return "upcoming";
    }

    public String computeStatus(Instant dueAt, String currentStatus) {
        if ("done".equals(currentStatus)) return "done";
        return computeStatus(dueAt);
    }
}
