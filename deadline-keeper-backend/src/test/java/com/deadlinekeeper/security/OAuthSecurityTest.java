package com.deadlinekeeper.security;

import com.deadlinekeeper.controller.CalendarController;
import com.deadlinekeeper.model.CalendarConnection;
import com.deadlinekeeper.repository.CalendarConnectionRepository;
import com.deadlinekeeper.service.CalendarSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OAuthSecurityTest {

    @Mock private CalendarSyncService calendarSyncService;
    @Mock private CalendarConnectionRepository connectionRepository;

    private CalendarController controller;
    private final UUID connectionId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final String validState = "valid-state-token-12345";
    private CalendarConnection conn;

    @BeforeEach
    void setUp() {
        controller = new CalendarController(calendarSyncService, connectionRepository);

        conn = new CalendarConnection();
        conn.setId(connectionId);
        conn.setUserId(userId);
        conn.setOauthState(validState);
        conn.setOauthStateExpiresAt(Instant.now().plusSeconds(300));
    }

    @Test
    @DisplayName("First callback with valid state succeeds")
    void firstCallbackSucceeds() {
        when(calendarSyncService.consumeStateAndGetUserId(validState)).thenReturn(userId);

        ResponseEntity<Map<String, String>> response = controller.callback("auth-code-xyz", validState);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("status", "connected");
        verify(calendarSyncService).handleCallback(userId, "auth-code-xyz");
    }

    @Test
    @DisplayName("Second callback with same state fails (replay attack prevented)")
    void replayCallbackFails() {
        // Atomic consume returns null because state was already consumed
        when(calendarSyncService.consumeStateAndGetUserId(validState)).thenReturn(null);

        ResponseEntity<Map<String, String>> response = controller.callback("auth-code-xyz", validState);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsKey("error");
        verify(calendarSyncService, never()).handleCallback(any(), any());
    }

    @Test
    @DisplayName("Missing state parameter is rejected")
    void missingStateFails() {
        ResponseEntity<Map<String, String>> response = controller.callback("auth-code-xyz", "");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(calendarSyncService, never()).handleCallback(any(), any());
    }

    @Test
    @DisplayName("Service-level atomic consume checks expiry and updates repository")
    void serviceAtomicConsume() {
        CalendarSyncService service = new CalendarSyncService(
                null, connectionRepository, null, null, null, null, null);

        when(connectionRepository.findByOauthState(validState)).thenReturn(Optional.of(conn));
        when(connectionRepository.consumeOauthState(connectionId, validState)).thenReturn(1);

        UUID consumedUserId = service.consumeStateAndGetUserId(validState);

        assertThat(consumedUserId).isEqualTo(userId);
        verify(connectionRepository).consumeOauthState(connectionId, validState);
    }

    @Test
    @DisplayName("Service-level atomic consume rejects expired state")
    void serviceRejectsExpiredState() {
        CalendarSyncService service = new CalendarSyncService(
                null, connectionRepository, null, null, null, null, null);

        conn.setOauthStateExpiresAt(Instant.now().minusSeconds(10));
        when(connectionRepository.findByOauthState(validState)).thenReturn(Optional.of(conn));

        UUID consumedUserId = service.consumeStateAndGetUserId(validState);

        assertThat(consumedUserId).isNull();
        verify(connectionRepository, never()).consumeOauthState(any(), any());
    }
}
