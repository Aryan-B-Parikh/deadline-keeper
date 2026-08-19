package com.deadlinekeeper.repository;

import com.deadlinekeeper.model.Reminder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ReminderRepository extends JpaRepository<Reminder, UUID> {

    List<Reminder> findByEventId(UUID eventId);

    List<Reminder> findByEventIdIn(List<UUID> eventIds);

    List<Reminder> findByEnabledTrue();
}
