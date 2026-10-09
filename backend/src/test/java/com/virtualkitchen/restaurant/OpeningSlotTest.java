package com.virtualkitchen.restaurant;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OpeningSlotTest {

    // 2026-10-05 is a Monday.
    private static LocalDateTime monday(String time) {
        return LocalDateTime.of(2026, 10, 5, 0, 0).with(LocalTime.parse(time));
    }

    @Test
    void sameDaySlotIsHalfOpenInterval() {
        OpeningSlot lunch = new OpeningSlot(DayOfWeek.MONDAY, LocalTime.of(11, 0), LocalTime.of(15, 0));

        assertThat(lunch.isOpenAt(monday("10:59"))).isFalse();
        assertThat(lunch.isOpenAt(monday("11:00"))).isTrue();
        assertThat(lunch.isOpenAt(monday("14:59"))).isTrue();
        assertThat(lunch.isOpenAt(monday("15:00"))).isFalse();
        assertThat(lunch.isOpenAt(monday("12:00").plusDays(1))).isFalse();
    }

    @Test
    void overnightSlotSpillsIntoNextDay() {
        OpeningSlot late = new OpeningSlot(DayOfWeek.MONDAY, LocalTime.of(18, 0), LocalTime.of(2, 0));

        assertThat(late.isOpenAt(monday("17:59"))).isFalse();
        assertThat(late.isOpenAt(monday("23:30"))).isTrue();
        assertThat(late.isOpenAt(monday("01:00"))).isFalse(); // Monday 1am belongs to Sunday's slot
        assertThat(late.isOpenAt(monday("01:59").plusDays(1))).isTrue(); // Tuesday 1:59am
        assertThat(late.isOpenAt(monday("02:00").plusDays(1))).isFalse();
    }

    @Test
    void sundayOvernightWrapsToMonday() {
        OpeningSlot sunday = new OpeningSlot(DayOfWeek.SUNDAY, LocalTime.of(20, 0), LocalTime.of(1, 0));

        assertThat(sunday.isOpenAt(monday("00:30"))).isTrue();
    }

    @Test
    void equalTimesMeanTwentyFourHours() {
        OpeningSlot allDay = new OpeningSlot(DayOfWeek.MONDAY, LocalTime.MIDNIGHT, LocalTime.MIDNIGHT);

        assertThat(allDay.isOpenAt(monday("00:00"))).isTrue();
        assertThat(allDay.isOpenAt(monday("23:59"))).isTrue();
        assertThat(allDay.isOpenAt(monday("00:00").plusDays(1))).isFalse();
    }

    @Test
    void noSlotsMeansClosed() {
        assertThat(OpeningSlot.isOpenAt(List.of(), monday("12:00"))).isFalse();
    }
}
