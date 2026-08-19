package com.deadlinekeeper.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReminderRequest {

    @Min(value = 0, message = "Offset seconds cannot be negative")
    private Long offsetSeconds;

    @NotBlank(message = "Channel is required")
    private String channel;
}
