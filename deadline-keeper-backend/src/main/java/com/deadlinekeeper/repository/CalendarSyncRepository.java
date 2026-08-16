package com.deadlinekeeper.repository;

import com.deadlinekeeper.model.CalendarSync;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CalendarSyncRepository extends JpaRepository<CalendarSync, UUID> {

    Optional<CalendarSync> findByUserId(UUID userId);
}
