package com.deadlinekeeper.dto;

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

    @NotNull(message = "Offset seconds is required")
    @Min(value = 0, message = "Offset seconds cannot be negative")
    private Long offsetSeconds;

    @NotBlank(message = "Channel is required")
    @Pattern(regexp = "email|in_app", message = "Channel must be email or in_app")
    private String channel;
}
