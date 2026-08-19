package com.deadlinekeeper.repository;

import com.deadlinekeeper.model.CalendarConnection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CalendarConnectionRepository extends JpaRepository<CalendarConnection, UUID> {

    Optional<CalendarConnection> findByUserId(UUID userId);
}
