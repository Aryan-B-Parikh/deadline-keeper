package com.deadlinekeeper.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class UserProfileUpdateRequest {
    private String displayName;
    private String timezone;
    private Map<String, Object> notificationPrefs;
}
