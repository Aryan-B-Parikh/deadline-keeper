package com.deadlinekeeper.service;

import com.deadlinekeeper.dto.ExtractConfirmRequest;
import com.deadlinekeeper.dto.EventResponse;
import com.deadlinekeeper.dto.ExtractionResult;
import com.deadlinekeeper.model.User;
import com.deadlinekeeper.notification.NotificationChannel;
import com.deadlinekeeper.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class InboxParseService {

    private static final Logger log = LoggerFactory.getLogger(InboxParseService.class);

    private final ExtractionService extractionService;
    private final UserRepository userRepository;
    private final List<NotificationChannel> notificationChannels;

    public InboxParseService(ExtractionService extractionService,
                             UserRepository userRepository,
                             List<NotificationChannel> notificationChannels) {
        this.extractionService = extractionService;
        this.userRepository = userRepository;
        this.notificationChannels = notificationChannels;
    }

    public List<EventResponse> processInboundEmail(String fromEmail, String subject,
                                                    String textBody, String htmlBody) {
        User user = userRepository.findByEmailIgnoreCase(fromEmail).orElse(null);

        if (user == null) {
            log.warn("Received inbox email from unknown sender: {}", fromEmail);
            return List.of();
        }

        String body = textBody != null && !textBody.isBlank() ? textBody : htmlBody;
        if (body == null || body.isBlank()) {
            log.warn("Empty email body from: {}", fromEmail);
            return List.of();
        }

        String combinedInput = "Subject: %s\n\nBody:\n%s".formatted(subject, body);
        ExtractionResult result = extractionService.extractFromText(combinedInput);

        if (result.getEvents() == null || result.getEvents().isEmpty()) {
            log.info("No deadlines extracted from email by: {}", fromEmail);
            return List.of();
        }

        List<ExtractionResult.ExtractedEvent> highConfidenceEvents = result.getEvents().stream()
                .filter(e -> e.getConfidenceScore() >= 0.7f)
                .toList();

        if (highConfidenceEvents.isEmpty()) {
            log.warn("All extracted events below confidence threshold (0.7) from: {}", fromEmail);
            return List.of();
        }

        ExtractConfirmRequest confirmRequest = new ExtractConfirmRequest();
        confirmRequest.setSourceType("email");
        String ref = "Email from %s: %s".formatted(fromEmail, subject);
        if (ref.length() > 200) ref = ref.substring(0, 200) + "...";
        confirmRequest.setSourceReference(ref);

        List<ExtractConfirmRequest.ConfirmedEvent> confirmedEvents = highConfidenceEvents.stream()
                .filter(e -> e.getDueDate() != null)
                .map(e -> {
                    ExtractConfirmRequest.ConfirmedEvent confirmed = new ExtractConfirmRequest.ConfirmedEvent();
                    confirmed.setTitle(e.getTitle());
                    confirmed.setType(e.getType());
                    confirmed.setDueDate(e.getDueDate());
                    confirmed.setDueTime(e.getDueTime());
                    confirmed.setTimezone(e.getTimezone());
                    confirmed.setReminders(List.of(
                            new com.deadlinekeeper.dto.ReminderRequest(604800L, "email"), // 7d
                            new com.deadlinekeeper.dto.ReminderRequest(86400L, "email"),  // 1d
                            new com.deadlinekeeper.dto.ReminderRequest(7200L, "email")    // 2h
                    ));
                    return confirmed;
                })
                .toList();

        if (confirmedEvents.isEmpty()) return List.of();

        confirmRequest.setEvents(confirmedEvents);
        List<EventResponse> savedEvents = extractionService.confirmAndSave(user.getId(), confirmRequest);

        // Send confirmation email
        sendConfirmationEmail(user, savedEvents);

        log.info("Created {} events from email by {}", savedEvents.size(), fromEmail);
        return savedEvents;
    }

    private void sendConfirmationEmail(User user, List<EventResponse> events) {
        String title = "✅ DeadlineKeeper: %d deadline(s) added".formatted(events.size());
        StringBuilder message = new StringBuilder("The following deadlines were extracted from your email:\n\n");
        for (EventResponse event : events) {
            message.append("• %s — Due: %s%s\n".formatted(
                    event.getTitle(),
                    event.getDueDate(),
                    event.getDueTime() != null ? " at " + event.getDueTime() : ""
            ));
        }

        for (NotificationChannel channel : notificationChannels) {
            if ("email".equals(channel.getChannelName())) {
                try {
                    channel.send(user, title, message.toString(), null);
                } catch (Exception e) {
                    log.error("Failed to send confirmation email: {}", e.getMessage());
                }
                break;
            }
        }
    }
}
