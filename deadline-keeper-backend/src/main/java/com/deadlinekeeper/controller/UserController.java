package com.deadlinekeeper.controller;

import com.deadlinekeeper.dto.UserProfileResponse;
import com.deadlinekeeper.dto.UserProfileUpdateRequest;
import com.deadlinekeeper.exception.ResourceNotFoundException;
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

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/profile")
    public ResponseEntity<UserProfileResponse> getProfile() {
        UUID userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new ResourceNotFoundException("User", "current");
        }
        return ResponseEntity.ok(userService.getProfile(userId));
    }

    @PutMapping("/profile")
    public ResponseEntity<UserProfileResponse> updateProfile(
            @RequestBody UserProfileUpdateRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new ResourceNotFoundException("User", "current");
        }
        return ResponseEntity.ok(userService.updateProfile(userId, request));
    }

    @GetMapping("/profile/forwarding-token")
    public ResponseEntity<Map<String, String>> getForwardingToken() {
        UUID userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new ResourceNotFoundException("User", "current");
        }
        String token = userService.getForwardingToken(userId);
        return ResponseEntity.ok(Map.of(
                "token", token,
                "address", "deadline+" + token + "@yourdomain.com"));
    }
}
