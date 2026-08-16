package com.deadlinekeeper.repository;

import com.deadlinekeeper.model.ReminderLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ReminderLogRepository extends JpaRepository<ReminderLog, UUID> {

    List<ReminderLog> findByEventId(UUID eventId);

    boolean existsByEventIdAndOffsetFired(UUID eventId, String offsetFired);
}
