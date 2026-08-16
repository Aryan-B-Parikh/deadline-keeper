package com.deadlinekeeper.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Getter
@Setter
public class EventRequest {

    @NotBlank
    private String title;

    @NotBlank
    private String type;

    @NotNull
    private LocalDate dueDate;

    private LocalTime dueTime;

    private String timezone;

    private List<String> reminderSchedule;

    private String notes;
}
