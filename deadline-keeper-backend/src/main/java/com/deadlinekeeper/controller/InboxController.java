package com.deadlinekeeper.controller;

import com.deadlinekeeper.config.SendGridConfig;
import com.deadlinekeeper.dto.EventResponse;
import com.deadlinekeeper.model.User;
import com.deadlinekeeper.repository.UserRepository;
import com.deadlinekeeper.service.InboxParseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/inbox")
public class InboxController {

    private static final Logger log = LoggerFactory.getLogger(InboxController.class);

    private final InboxParseService inboxParseService;
    private final SendGridConfig sendGridConfig;
    private final UserRepository userRepository;

    public InboxController(InboxParseService inboxParseService,
                           SendGridConfig sendGridConfig,
                           UserRepository userRepository) {
        this.inboxParseService = inboxParseService;
        this.sendGridConfig = sendGridConfig;
        this.userRepository = userRepository;
    }

    @PostMapping("/webhook/{token}")
    public ResponseEntity<Map<String, String>> handleInboundEmail(
            @PathVariable String token,
            @RequestParam("from") String from,
            @RequestParam("subject") String subject,
            @RequestParam(value = "text", required = false) String textBody,
            @RequestParam(value = "html", required = false) String htmlBody) {

        String configuredToken = sendGridConfig.getWebhookToken();
        if (configuredToken == null || configuredToken.isBlank()) {
            log.error("Webhook rejected: SENDGRID_WEBHOOK_TOKEN is not configured (fail-closed)");
            return ResponseEntity.status(401).body(Map.of("error", "Webhook authentication not configured"));
        }

        if (!configuredToken.equals(token)) {
            log.warn("Invalid webhook token received");
            return ResponseEntity.status(401).body(Map.of("error", "Invalid token"));
        }

        log.info("Received inbound email from: {} subject: {}", from, subject);

        // Look up user by forwarding token from the URL path first
        User user = userRepository.findByForwardingToken(token).orElse(null);

        // Fallback: look up by sender email
        if (user == null) {
            String email = extractEmail(from);
            user = userRepository.findByEmailIgnoreCase(email).orElse(null);
        }

        if (user == null) {
            log.warn("No user found for forwarding token or email: {} / {}", token, from);
            return ResponseEntity.ok(Map.of("status", "ignored", "events_created", "0"));
        }

        List<EventResponse> events = inboxParseService.processInboundEmail(
                user.getEmail(), subject, textBody, htmlBody);

        return ResponseEntity.ok(Map.of(
                "status", "processed",
                "events_created", String.valueOf(events.size())));
    }

    private String extractEmail(String fromField) {
        if (fromField == null) return "";
        int start = fromField.indexOf('<');
        int end = fromField.indexOf('>');
        if (start >= 0 && end > start) {
            return fromField.substring(start + 1, end).trim();
        }
        return fromField.trim();
    }
}
