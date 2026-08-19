package com.deadlinekeeper.service;

import com.deadlinekeeper.config.GoogleCalendarConfig;
import com.deadlinekeeper.exception.ExternalServiceException;
import com.deadlinekeeper.exception.ResourceNotFoundException;
import com.deadlinekeeper.model.CalendarConnection;
import com.deadlinekeeper.model.Event;
import com.deadlinekeeper.model.ExternalEvent;
import com.deadlinekeeper.repository.CalendarConnectionRepository;
import com.deadlinekeeper.repository.EventRepository;
import com.deadlinekeeper.repository.ExternalEventRepository;
import com.deadlinekeeper.security.TokenEncryption;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.api.services.calendar.model.Events;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class CalendarSyncService {

    private static final Logger log = LoggerFactory.getLogger(CalendarSyncService.class);
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final List<String> SCOPES = List.of("https://www.googleapis.com/auth/calendar.readonly");
    private static final int PAGE_SIZE = 250;

    private static final Set<String> DEADLINE_KEYWORDS = Set.of(
            "exam", "test", "quiz", "assignment", "homework", "project", "submission",
            "deadline", "due", "hackathon", "interview", "presentation", "midterm", "final"
    );

    private final GoogleCalendarConfig config;
    private final CalendarConnectionRepository connectionRepository;
    private final ExternalEventRepository externalEventRepository;
    private final EventRepository eventRepository;
    private final DeadlineStatusService statusService;
    private final TokenEncryption tokenEncryption;
    private final ReminderService reminderService;

    public CalendarSyncService(GoogleCalendarConfig config,
                               CalendarConnectionRepository connectionRepository,
                               ExternalEventRepository externalEventRepository,
                               EventRepository eventRepository,
                               DeadlineStatusService statusService,
                               TokenEncryption tokenEncryption,
                               ReminderService reminderService) {
        this.config = config;
        this.connectionRepository = connectionRepository;
        this.externalEventRepository = externalEventRepository;
        this.eventRepository = eventRepository;
        this.statusService = statusService;
        this.tokenEncryption = tokenEncryption;
        this.reminderService = reminderService;
    }

    public String getAuthorizationUrl(String state) {
        try {
            HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
            GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                    httpTransport, JSON_FACTORY,
                    config.getClientId(), config.getClientSecret(), SCOPES)
                    .setAccessType("offline")
                    .build();

            return flow.newAuthorizationUrl()
                    .setRedirectUri(config.getRedirectUri())
                    .setState(state)
                    .build();
        } catch (Exception e) {
            throw new ExternalServiceException("Google Calendar",
                    "Failed to build authorization URL: " + e.getMessage(), e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID consumeStateAndGetUserId(String state) {
        if (state == null || state.isBlank()) return null;

        CalendarConnection conn = connectionRepository.findByOauthState(state).orElse(null);
        if (conn == null || conn.getOauthStateExpiresAt() == null || Instant.now().isAfter(conn.getOauthStateExpiresAt())) {
            return null;
        }

        int consumed = connectionRepository.consumeOauthState(conn.getId(), state);
        return consumed == 1 ? conn.getUserId() : null;
    }

    public void handleCallback(UUID userId, String authorizationCode) {
        try {
            HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
            GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                    httpTransport, JSON_FACTORY,
                    config.getClientId(), config.getClientSecret(), SCOPES)
                    .setAccessType("offline")
                    .build();

            GoogleTokenResponse tokenResponse = flow.newTokenRequest(authorizationCode)
                    .setRedirectUri(config.getRedirectUri())
                    .execute();

            CalendarConnection conn = connectionRepository.findByUserId(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("Calendar connection", userId.toString()));
            conn.setProvider("google");
            conn.setEncryptedAccessToken(tokenEncryption.encrypt(tokenResponse.getAccessToken()));
            if (tokenResponse.getRefreshToken() != null && !tokenResponse.getRefreshToken().isBlank()) {
                conn.setEncryptedRefreshToken(tokenEncryption.encrypt(tokenResponse.getRefreshToken()));
            }
            connectionRepository.save(conn);
            syncEvents(userId);
        } catch (ExternalServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalServiceException("Google Calendar",
                    "OAuth callback failed: " + e.getMessage(), e);
        }
    }

    public void syncEvents(UUID userId) {
        syncEvents(userId, false);
    }

    private void syncEvents(UUID userId, boolean retriedAfterExpiredToken) {
        CalendarConnection conn = connectionRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Calendar connection", userId.toString()));

        try {
            Calendar calendarService = buildCalendarService(conn);
            if (conn.getSyncToken() == null) {
                fullSync(userId, calendarService, conn);
            } else {
                incrementalSync(userId, calendarService, conn);
            }

            conn.setLastSyncedAt(Instant.now());
            connectionRepository.save(conn);
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (!retriedAfterExpiredToken && (msg.contains("410") || msg.contains("GONE"))) {
                log.warn("Sync token expired for user {}, performing one full resync", userId);
                conn.setSyncToken(null);
                connectionRepository.save(conn);
                syncEvents(userId, true);
            } else {
                throw new ExternalServiceException("Google Calendar", "Sync failed: " + e.getMessage(), e);
            }
        }
    }

    private void fullSync(UUID userId, Calendar calendarService, CalendarConnection conn) throws IOException {
        String pageToken = null;
        String nextSyncToken = null;

        do {
            var request = calendarService.events().list("primary")
                    .setMaxResults(PAGE_SIZE)
                    .setTimeMin(new DateTime(System.currentTimeMillis()))
                    .setSingleEvents(true)
                    .setOrderBy("startTime");
            if (pageToken != null) request.setPageToken(pageToken);

            Events events = request.execute();
            List<com.google.api.services.calendar.model.Event> items = events.getItems();
            if (items != null) {
                for (com.google.api.services.calendar.model.Event googleEvent : items) {
                    importGoogleEvent(userId, googleEvent);
                }
            }

            pageToken = events.getNextPageToken();
            if (events.getNextSyncToken() != null) nextSyncToken = events.getNextSyncToken();
        } while (pageToken != null);

        if (nextSyncToken == null) {
            throw new ExternalServiceException("Google Calendar", "Full sync completed without a sync token");
        }
        conn.setSyncToken(nextSyncToken);
        log.info("Full sync completed for user {}", userId);
    }

    private void incrementalSync(UUID userId, Calendar calendarService, CalendarConnection conn) throws IOException {
        String pageToken = null;
        String nextSyncToken = null;

        do {
            var request = calendarService.events().list("primary")
                    .setMaxResults(PAGE_SIZE)
                    .setSyncToken(conn.getSyncToken());
            if (pageToken != null) request.setPageToken(pageToken);

            Events events = request.execute();
            List<com.google.api.services.calendar.model.Event> items = events.getItems();
            if (items != null) {
                for (com.google.api.services.calendar.model.Event googleEvent : items) {
                    if ("cancelled".equals(googleEvent.getStatus())) {
                        handleDeletedEvent(userId, googleEvent.getId());
                    } else {
                        importGoogleEvent(userId, googleEvent);
                    }
                }
            }

            nextSyncToken = events.getNextSyncToken();
            pageToken = events.getNextPageToken();
        } while (pageToken != null);

        if (nextSyncToken != null) conn.setSyncToken(nextSyncToken);
        log.info("Incremental sync completed for user {}", userId);
    }

    private void importGoogleEvent(UUID userId, com.google.api.services.calendar.model.Event googleEvent) {
        if (googleEvent.getStart() == null || googleEvent.getSummary() == null) return;
        if (!isDeadlineWorthy(googleEvent)) return;

        String externalId = googleEvent.getId();
        Optional<ExternalEvent> existing = externalEventRepository.findByProviderAndExternalId("google", externalId);
        if (existing.isPresent()) {
            ExternalEvent ext = existing.get();
            Event event = eventRepository.findById(ext.getDeadlineId()).orElse(null);
            if (event == null || !event.getUserId().equals(userId)) return;

            String newEtag = googleEvent.getEtag();
            if (newEtag != null && !newEtag.equals(ext.getEtag())) {
                updateEventFromGoogle(event, googleEvent);
                ext.setEtag(newEtag);
                ext.setExternalUpdatedAt(Instant.now());
                externalEventRepository.save(ext);
            }
            return;
        }

        Instant dueAt = parseGoogleEventDateTime(googleEvent);
        if (dueAt == null) return;

        String tz = googleEvent.getStart().getTimeZone() != null ? googleEvent.getStart().getTimeZone() : "UTC";
        try {
            ZoneId.of(tz);
        } catch (Exception e) {
            tz = "UTC";
        }

        Event event = new Event();
        event.setUserId(userId);
        event.setTitle(googleEvent.getSummary());
        event.setType(classifyEventType(googleEvent.getSummary()));
        event.setDueAt(dueAt);
        event.setTimezone(tz);
        event.setSource("calendar_sync");
        event.setSourceReference("calendar:" + externalId);
        event.setAiConfidence(0.8f);
        event.setConfirmationStatus("auto_imported");
        event.setUserConfirmed(false);
        event.setStatus(statusService.computeStatus(dueAt));
        event.setNotes(googleEvent.getDescription());

        Event saved = eventRepository.save(event);
        List<com.deadlinekeeper.dto.ReminderRequest> defaultReminders = List.of(
                new com.deadlinekeeper.dto.ReminderRequest(86400L, "in_app"),
                new com.deadlinekeeper.dto.ReminderRequest(7200L, "in_app")
        );
        reminderService.syncFromSchedule(saved, defaultReminders);

        ExternalEvent extEvent = new ExternalEvent();
        extEvent.setDeadlineId(saved.getId());
        extEvent.setProvider("google");
        extEvent.setExternalId(externalId);
        extEvent.setEtag(googleEvent.getEtag());
        extEvent.setExternalUpdatedAt(Instant.now());
        externalEventRepository.save(extEvent);
    }

    private void handleDeletedEvent(UUID userId, String externalId) {
        Optional<ExternalEvent> ext = externalEventRepository.findByProviderAndExternalId("google", externalId);
        if (ext.isPresent()) {
            Event event = eventRepository.findById(ext.get().getDeadlineId()).orElse(null);
            if (event != null && event.getUserId().equals(userId)) {
                event.setStatus("cancelled");
                eventRepository.save(event);
            }
            externalEventRepository.delete(ext.get());
        }
    }

    private boolean isDeadlineWorthy(com.google.api.services.calendar.model.Event googleEvent) {
        String summary = googleEvent.getSummary() != null ? googleEvent.getSummary().toLowerCase() : "";
        String description = googleEvent.getDescription() != null ? googleEvent.getDescription().toLowerCase() : "";
        return DEADLINE_KEYWORDS.stream().anyMatch(keyword -> summary.contains(keyword) || description.contains(keyword));
    }

    private Instant parseGoogleEventDateTime(com.google.api.services.calendar.model.Event googleEvent) {
        EventDateTime start = googleEvent.getStart();
        try {
            if (start.getDateTime() != null) {
                return Instant.parse(start.getDateTime().toStringRfc3339());
            }
            if (start.getDate() != null) {
                LocalDate date = LocalDate.parse(start.getDate().toStringRfc3339().substring(0, 10));
                String tz = start.getTimeZone() != null ? start.getTimeZone() : "UTC";
                try {
                    return date.atTime(LocalTime.MAX).atZone(ZoneId.of(tz)).toInstant();
                } catch (Exception ignored) {
                    return date.atTime(LocalTime.MAX).atZone(ZoneOffset.UTC).toInstant();
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse calendar event datetime: {}", e.getMessage());
        }
        return null;
    }

    private String classifyEventType(String summary) {
        String lower = summary.toLowerCase();
        if (lower.contains("exam") || lower.contains("test") || lower.contains("quiz") || lower.contains("midterm") || lower.contains("final")) return "exam";
        if (lower.contains("assignment") || lower.contains("homework") || lower.contains("project") || lower.contains("submission")) return "submission";
        if (lower.contains("hackathon")) return "hackathon";
        return "other";
    }

    private void updateEventFromGoogle(Event event, com.google.api.services.calendar.model.Event googleEvent) {
        Instant newDueAt = parseGoogleEventDateTime(googleEvent);
        if (newDueAt != null) {
            event.setDueAt(newDueAt);
            event.setStatus(statusService.computeStatus(newDueAt, event.getStatus()));
        }
        if (googleEvent.getSummary() != null) event.setTitle(googleEvent.getSummary());
        if (googleEvent.getDescription() != null) event.setNotes(googleEvent.getDescription());
        eventRepository.save(event);
    }

    private Calendar buildCalendarService(CalendarConnection conn) throws Exception {
        String accessToken = tokenEncryption.decrypt(conn.getEncryptedAccessToken());
        String refreshToken = tokenEncryption.decrypt(conn.getEncryptedRefreshToken());

        HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                httpTransport, JSON_FACTORY,
                config.getClientId(), config.getClientSecret(), SCOPES)
                .setAccessType("offline")
                .build();

        Credential credential = flow.createAndStoreCredential(
                new GoogleTokenResponse()
                        .setAccessToken(accessToken)
                        .setRefreshToken(refreshToken),
                conn.getUserId().toString());

        return new Calendar.Builder(httpTransport, JSON_FACTORY, credential)
                .setApplicationName("DeadlineKeeper")
                .build();
    }

    public void disconnect(UUID userId) {
        connectionRepository.findByUserId(userId).ifPresent(connectionRepository::delete);
    }
}
