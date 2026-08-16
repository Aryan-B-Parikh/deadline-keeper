package com.deadlinekeeper.service;

import com.deadlinekeeper.config.GoogleCalendarConfig;
import com.deadlinekeeper.model.CalendarSync;
import com.deadlinekeeper.model.Event;
import com.deadlinekeeper.repository.CalendarSyncRepository;
import com.deadlinekeeper.repository.EventRepository;
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

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class CalendarSyncService {

    private static final Logger log = LoggerFactory.getLogger(CalendarSyncService.class);
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final List<String> SCOPES = List.of("https://www.googleapis.com/auth/calendar.readonly");

    private final GoogleCalendarConfig config;
    private final CalendarSyncRepository calendarSyncRepository;
    private final EventRepository eventRepository;

    public CalendarSyncService(GoogleCalendarConfig config,
                               CalendarSyncRepository calendarSyncRepository,
                               EventRepository eventRepository) {
        this.config = config;
        this.calendarSyncRepository = calendarSyncRepository;
        this.eventRepository = eventRepository;
    }

    public String getAuthorizationUrl() {
        try {
            HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
            GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                    httpTransport, JSON_FACTORY,
                    config.getClientId(), config.getClientSecret(), SCOPES)
                    .setAccessType("offline")
                    .build();

            return flow.newAuthorizationUrl()
                    .setRedirectUri(config.getRedirectUri())
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to build authorization URL: " + e.getMessage(), e);
        }
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

            CalendarSync sync = calendarSyncRepository.findByUserId(userId)
                    .orElse(new CalendarSync());
            sync.setUserId(userId);
            sync.setGoogleAccessToken(tokenResponse.getAccessToken());
            sync.setGoogleRefreshToken(tokenResponse.getRefreshToken());
            calendarSyncRepository.save(sync);

            syncEvents(userId);
        } catch (Exception e) {
            throw new RuntimeException("OAuth callback failed: " + e.getMessage(), e);
        }
    }

    public void syncEvents(UUID userId) {
        Optional<CalendarSync> syncOpt = calendarSyncRepository.findByUserId(userId);
        if (syncOpt.isEmpty()) {
            throw new RuntimeException("Google Calendar not connected");
        }

        CalendarSync sync = syncOpt.get();
        try {
            HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
            GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                    httpTransport, JSON_FACTORY,
                    config.getClientId(), config.getClientSecret(), SCOPES)
                    .setAccessType("offline")
                    .build();

            Credential credential = flow.createAndStoreCredential(
                    new GoogleTokenResponse()
                            .setAccessToken(sync.getGoogleAccessToken())
                            .setRefreshToken(sync.getGoogleRefreshToken()),
                    userId.toString());

            Calendar calendarService = new Calendar.Builder(httpTransport, JSON_FACTORY, credential)
                    .setApplicationName("DeadlineKeeper")
                    .build();

            Calendar.ListRequest request = calendarService.events().list("primary")
                    .setMaxResults(50)
                    .setTimeMin(new DateTime(System.currentTimeMillis()))
                    .setSingleEvents(true)
                    .setOrderBy("startTime");

            if (sync.getSyncToken() != null) {
                request.setSyncToken(sync.getSyncToken());
            }

            Events events = request.execute();
            List<com.google.api.services.calendar.model.Event> items = events.getItems();

            if (items != null) {
                for (com.google.api.services.calendar.model.Event googleEvent : items) {
                    importGoogleEvent(userId, googleEvent);
                }
            }

            sync.setSyncToken(events.getNextSyncToken());
            sync.setLastSyncedAt(Instant.now());
            calendarSyncRepository.save(sync);

            log.info("Synced {} events for user {}", items != null ? items.size() : 0, userId);
        } catch (Exception e) {
            log.error("Calendar sync failed for user {}: {}", userId, e.getMessage(), e);
            throw new RuntimeException("Calendar sync failed: " + e.getMessage(), e);
        }
    }

    private void importGoogleEvent(UUID userId, com.google.api.services.calendar.model.Event googleEvent) {
        if (googleEvent.getStart() == null || googleEvent.getSummary() == null) return;

        LocalDate dueDate;
        LocalTime dueTime = null;

        EventDateTime start = googleEvent.getStart();
        if (start.getDate() != null) {
            dueDate = LocalDate.parse(start.getDate().toStringRfc3339().substring(0, 10));
        } else if (start.getDateTime() != null) {
            String dateTimeStr = start.getDateTime().toStringRfc3339();
            dueDate = LocalDate.parse(dateTimeStr.substring(0, 10));
            if (dateTimeStr.length() > 11) {
                try {
                    dueTime = LocalTime.parse(dateTimeStr.substring(11, 16));
                } catch (Exception ignored) {}
            }
        } else {
            return;
        }

        String sourceRef = "calendar:" + googleEvent.getId();
        List<Event> existing = eventRepository.findByUserIdAndSourceAndSourceReference(
                userId, "calendar_sync", sourceRef);
        if (!existing.isEmpty()) {
            return;
        }

        Event event = new Event();
        event.setUserId(userId);
        event.setTitle(googleEvent.getSummary());
        event.setType("other");
        event.setDueDate(dueDate);
        event.setDueTime(dueTime);
        event.setTimezone(start.getTimeZone() != null ? start.getTimeZone() : "UTC");
        event.setSource("calendar_sync");
        event.setSourceReference(sourceRef);
        event.setConfidenceScore(0.8f);
        event.setReminderSchedule(List.of("1d", "2h"));
        event.setNotes(googleEvent.getDescription());
        eventRepository.save(event);
    }

    public void disconnect(UUID userId) {
        calendarSyncRepository.findByUserId(userId)
                .ifPresent(calendarSyncRepository::delete);
    }
}
