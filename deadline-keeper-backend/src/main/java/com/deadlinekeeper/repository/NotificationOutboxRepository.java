package com.deadlinekeeper.repository;

import com.deadlinekeeper.model.NotificationOutbox;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, UUID> {

    List<NotificationOutbox> findByStatusInOrderByScheduledAtAsc(List<String> statuses, Pageable pageable);

    Optional<NotificationOutbox> findByIdempotencyKey(String idempotencyKey);

    long countByStatus(String status);
}
