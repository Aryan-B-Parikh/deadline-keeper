package com.deadlinekeeper.repository;

import com.deadlinekeeper.model.CalendarConnection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CalendarConnectionRepository extends JpaRepository<CalendarConnection, UUID> {

    Optional<CalendarConnection> findByUserId(UUID userId);

    Optional<CalendarConnection> findByOauthState(String oauthState);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(value = """
            UPDATE calendar_connections
            SET oauth_state = NULL,
                oauth_state_expires_at = NULL,
                updated_at = NOW()
            WHERE id = :id
              AND oauth_state = :state
              AND oauth_state_expires_at >= NOW()
            """, nativeQuery = true)
    int consumeOauthState(@org.springframework.data.repository.query.Param("id") UUID id,
                         @org.springframework.data.repository.query.Param("state") String state);
}
