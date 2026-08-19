package com.deadlinekeeper.controller;

import com.deadlinekeeper.config.SendGridConfig;
import com.deadlinekeeper.dto.EventResponse;
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

    public InboxController(InboxParseService inboxParseService, SendGridConfig sendGridConfig) {
        this.inboxParseService = inboxParseService;
        this.sendGridConfig = sendGridConfig;
    }

    @PostMapping("/webhook/{token}")
    public ResponseEntity<Map<String, String>> handleInboundEmail(
            @PathVariable String token,
            @RequestParam("from") String from,
            @RequestParam("subject") String subject,
            @RequestParam(value = "text", required = false) String textBody,
            @RequestParam(value = "html", required = false) String htmlBody) {

        String configuredToken = sendGridConfig.getWebhookToken();
        if (configuredToken != null && !configuredToken.isBlank()) {
            if (!configuredToken.equals(token)) {
                log.warn("Invalid webhook token received");
                return ResponseEntity.status(401).body(Map.of("error", "Invalid token"));
            }
        } else {
            log.warn("WARNING: Webhook token validation disabled. Set sendgrid.webhook-token for production.");
        }

        log.info("Received inbound email from: {} subject: {}", from, subject);

        String email = extractEmail(from);
        List<EventResponse> events = inboxParseService.processInboundEmail(
                email, subject, textBody, htmlBody);

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
