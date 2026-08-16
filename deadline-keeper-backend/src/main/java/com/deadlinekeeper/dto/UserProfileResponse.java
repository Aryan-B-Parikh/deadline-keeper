package com.deadlinekeeper.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@Builder
public class UserProfileResponse {
    private String email;
    private String displayName;
    private String timezone;
    private String plan;
    private Map<String, Object> notificationPrefs;
}
