package com.virtualkitchen.restaurant.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.virtualkitchen.restaurant.OpeningSlot;
import jakarta.validation.constraints.NotNull;

import java.time.DayOfWeek;
import java.time.LocalTime;

/** Times are "HH:mm" in Asia/Kolkata. {@code closes <= opens} means the slot ends after midnight. */
public record OpeningSlotDto(
        @NotNull DayOfWeek day,
        @NotNull @JsonFormat(pattern = "HH:mm") LocalTime opens,
        @NotNull @JsonFormat(pattern = "HH:mm") LocalTime closes) {

    public static OpeningSlotDto from(OpeningSlot slot) {
        return new OpeningSlotDto(slot.getDay(), slot.getOpensAt(), slot.getClosesAt());
    }

    public OpeningSlot toSlot() {
        return new OpeningSlot(day, opens, closes);
    }
}
