package com.deadlinekeeper.controller;

import com.deadlinekeeper.config.SendGridConfig;
import com.deadlinekeeper.dto.EventResponse;
import com.deadlinekeeper.service.InboxParseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
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

    @PostMapping("/webhook")
    public ResponseEntity<Map<String, String>> handleInboundEmail(
            @RequestHeader(value = "X-Twilio-Email-Event-Webhook-Signature", required = false) String signature,
            @RequestHeader(value = "X-Twilio-Email-Event-Webhook-Timestamp", required = false) String timestamp,
            @RequestParam("from") String from,
            @RequestParam("subject") String subject,
            @RequestParam(value = "text", required = false) String textBody,
            @RequestParam(value = "html", required = false) String htmlBody) {

        if (sendGridConfig.getWebhookSecret() != null && !sendGridConfig.getWebhookSecret().isBlank()) {
            if (!validateWebhookSignature(signature, timestamp, from + subject + textBody)) {
                log.warn("Invalid webhook signature from {}", from);
                return ResponseEntity.status(401).body(Map.of("error", "Invalid signature"));
            }
        }

        log.info("Received inbound email from: {} subject: {}", from, subject);

        String email = extractEmail(from);
        List<EventResponse> events = inboxParseService.processInboundEmail(
                email, subject, textBody, htmlBody);

        return ResponseEntity.ok(Map.of(
                "status", "processed",
                "events_created", String.valueOf(events.size())));
    }

    private boolean validateWebhookSignature(String signature, String timestamp, String body) {
        if (signature == null || timestamp == null) return false;
        try {
            String payload = timestamp + body;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(sendGridConfig.getWebhookSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String computed = Base64.getEncoder().encodeToString(hash);
            return computed.equals(signature);
        } catch (Exception e) {
            log.error("Webhook signature validation failed", e);
            return false;
        }
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
