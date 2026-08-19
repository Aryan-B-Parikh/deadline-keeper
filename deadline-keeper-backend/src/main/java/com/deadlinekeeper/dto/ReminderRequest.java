package com.deadlinekeeper.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReminderRequest {

    private static final long MAX_OFFSET_SECONDS = 7 * 24 * 60 * 60L;

    @NotNull(message = "Offset seconds is required")
    @Min(value = 0, message = "Offset seconds cannot be negative")
    @Max(value = MAX_OFFSET_SECONDS, message = "Reminder offset cannot exceed 7 days")
    private Long offsetSeconds;

    @NotBlank(message = "Channel is required")
    @Pattern(regexp = "email|in_app", message = "Channel must be email or in_app")
    private String channel;
}
