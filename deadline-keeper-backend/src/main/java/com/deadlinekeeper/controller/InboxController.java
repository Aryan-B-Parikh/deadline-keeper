package com.deadlinekeeper.controller;

import com.deadlinekeeper.dto.EventResponse;
import com.deadlinekeeper.model.User;
import com.deadlinekeeper.repository.UserRepository;
import com.deadlinekeeper.service.InboxParseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/inbox")
public class InboxController {

    private static final Logger log = LoggerFactory.getLogger(InboxController.class);

    private final InboxParseService inboxParseService;
    private final UserRepository userRepository;

    public InboxController(InboxParseService inboxParseService,
                           UserRepository userRepository) {
        this.inboxParseService = inboxParseService;
        this.userRepository = userRepository;
    }

    @PostMapping({"/webhook", "/webhook/{pathToken}"})
    public ResponseEntity<Map<String, String>> handleInboundEmail(
            @PathVariable(required = false) String pathToken,
            @RequestParam("from") String from,
            @RequestParam("subject") String subject,
            @RequestParam(value = "to", required = false) String to,
            @RequestParam(value = "text", required = false) String textBody,
            @RequestParam(value = "html", required = false) String htmlBody) {

        String token = pathToken != null ? pathToken : extractForwardingToken(to);
        if (token == null || token.isBlank()) {
            log.warn("Inbound email rejected: missing forwarding token");
            return ResponseEntity.status(401).body(Map.of("error", "Invalid token"));
        }

        User user = userRepository.findByForwardingToken(token).orElse(null);
        if (user == null || !MessageDigest.isEqual(
                user.getForwardingToken().getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8))) {
            log.warn("Inbound email rejected: invalid forwarding token");
            return ResponseEntity.status(401).body(Map.of("error", "Invalid token"));
        }

        log.info("Received inbound email from: {} subject: {}", from, subject);

        List<EventResponse> events = inboxParseService.processInboundEmail(
                user.getEmail(), subject, textBody, htmlBody);

        return ResponseEntity.ok(Map.of(
                "status", "processed",
                "events_created", String.valueOf(events.size())));
    }

    private String extractForwardingToken(String toField) {
        if (toField == null || toField.isBlank()) return null;

        String address = toField.trim();
        int start = address.indexOf('<');
        int end = address.indexOf('>');
        if (start >= 0 && end > start) {
            address = address.substring(start + 1, end).trim();
        }

        int plus = address.indexOf('+');
        int at = address.indexOf('@', plus + 1);
        if (plus < 0 || at <= plus + 1) return null;
        return address.substring(plus + 1, at);
    }
}
