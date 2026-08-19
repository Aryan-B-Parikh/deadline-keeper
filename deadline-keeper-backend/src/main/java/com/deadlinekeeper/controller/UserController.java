package com.deadlinekeeper.controller;

import com.deadlinekeeper.config.SendGridConfig;
import com.deadlinekeeper.dto.UserProfileResponse;
import com.deadlinekeeper.dto.UserProfileUpdateRequest;
import com.deadlinekeeper.security.SecurityUtils;
import com.deadlinekeeper.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;
    private final SendGridConfig sendGridConfig;

    public UserController(UserService userService, SendGridConfig sendGridConfig) {
        this.userService = userService;
        this.sendGridConfig = sendGridConfig;
    }

    @GetMapping("/profile")
    public ResponseEntity<UserProfileResponse> getProfile() {
        return ResponseEntity.ok(userService.getProfile(SecurityUtils.getCurrentUserId()));
    }

    @PutMapping("/profile")
    public ResponseEntity<UserProfileResponse> updateProfile(
            @RequestBody UserProfileUpdateRequest request) {
        return ResponseEntity.ok(userService.updateProfile(SecurityUtils.getCurrentUserId(), request));
    }

    @GetMapping("/profile/forwarding-token")
    public ResponseEntity<Map<String, String>> getForwardingToken() {
        UUID userId = SecurityUtils.getCurrentUserId();
        String token = userService.getForwardingToken(userId);
        String domain = sendGridConfig.getInboxParseDomain();
        if (domain == null || domain.isBlank()) domain = "yourdomain.com";

        return ResponseEntity.ok(Map.of(
                "token", token,
                "address", "deadline+" + token + "@" + domain));
    }
}
