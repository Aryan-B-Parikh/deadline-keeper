package com.deadlinekeeper.repository;

import com.deadlinekeeper.model.ReminderDelivery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReminderDeliveryRepository extends JpaRepository<ReminderDelivery, UUID> {

    boolean existsByEventIdAndReminderIdAndChannel(UUID eventId, UUID reminderId, String channel);

    Optional<ReminderDelivery> findByEventIdAndReminderIdAndChannel(UUID eventId, UUID reminderId, String channel);

    List<ReminderDelivery> findByStatusIn(List<String> statuses);
}

