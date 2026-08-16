package com.deadlinekeeper.service;

import com.deadlinekeeper.dto.UserProfileResponse;
import com.deadlinekeeper.dto.UserProfileUpdateRequest;
import com.deadlinekeeper.model.User;
import com.deadlinekeeper.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public UserProfileResponse getProfile(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return toResponse(user);
    }

    public UserProfileResponse updateProfile(UUID userId, UserProfileUpdateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (request.getDisplayName() != null) user.setDisplayName(request.getDisplayName());
        if (request.getTimezone() != null) user.setTimezone(request.getTimezone());
        if (request.getNotificationPrefs() != null) user.setNotificationPrefs(request.getNotificationPrefs());

        return toResponse(userRepository.save(user));
    }

    private UserProfileResponse toResponse(User user) {
        return UserProfileResponse.builder()
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .timezone(user.getTimezone())
                .plan(user.getPlan())
                .notificationPrefs(user.getNotificationPrefs())
                .build();
    }
}
