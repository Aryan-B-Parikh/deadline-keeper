package com.deadlinekeeper.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReminderResponse {
    private UUID id;
    private Long offsetSeconds;
    private String channel;
    private Boolean enabled;
}
