package com.deadlinekeeper.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Getter
@Setter
public class EventRequest {

    @NotBlank(message = "Title is required")
    @Size(max = 500, message = "Title must be under 500 characters")
    private String title;

    @NotBlank(message = "Type is required")
    private String type;

    private Instant dueAt;



    private String timezone;



    private List<ReminderRequest> reminders;

    @Size(max = 5000, message = "Notes must be under 5000 characters")
    private String notes;
}
