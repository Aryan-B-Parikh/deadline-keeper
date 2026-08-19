package com.deadlinekeeper.service;

import com.deadlinekeeper.dto.UserProfileResponse;
import com.deadlinekeeper.dto.UserProfileUpdateRequest;
import com.deadlinekeeper.exception.ResourceNotFoundException;
import com.deadlinekeeper.exception.ValidationException;
import com.deadlinekeeper.model.User;
import com.deadlinekeeper.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public UserProfileResponse getProfile(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId.toString()));
        return toResponse(user);
    }

    public UserProfileResponse updateProfile(UUID userId, UserProfileUpdateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId.toString()));

        if (request.getDisplayName() != null) user.setDisplayName(request.getDisplayName());
        if (request.getTimezone() != null) {
            try {
                ZoneId.of(request.getTimezone());
            } catch (Exception e) {
                throw new ValidationException("Invalid timezone: " + request.getTimezone());
            }
            user.setTimezone(request.getTimezone());
        }
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

    public String getForwardingToken(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId.toString()));
        return user.getForwardingToken();
    }
}
