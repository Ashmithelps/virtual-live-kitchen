package com.virtualkitchen.restaurant;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collection;

/**
 * One opening period on a weekday, in the platform timezone. If {@code closesAt} is not after
 * {@code opensAt} the period runs past midnight into the next day ({@code 18:00–02:00});
 * equal times mean open for 24 hours from {@code opensAt}.
 *
 * <p>The same rule is expressed in SQL in {@code RestaurantSearchService#OPEN_NOW_SQL}; keep them in sync.
 */
@Embeddable
public class OpeningSlot {

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 9)
    private DayOfWeek day;

    @Column(name = "opens_at", nullable = false)
    private LocalTime opensAt;

    @Column(name = "closes_at", nullable = false)
    private LocalTime closesAt;

    protected OpeningSlot() {
        // for JPA
    }

    public OpeningSlot(DayOfWeek day, LocalTime opensAt, LocalTime closesAt) {
        this.day = day;
        this.opensAt = opensAt;
        this.closesAt = closesAt;
    }

    public boolean crossesMidnight() {
        return !closesAt.isAfter(opensAt);
    }

    public boolean isOpenAt(LocalDateTime localTime) {
        DayOfWeek today = localTime.getDayOfWeek();
        LocalTime time = localTime.toLocalTime();
        if (day == today && !time.isBefore(opensAt) && (crossesMidnight() || time.isBefore(closesAt))) {
            return true;
        }
        // The tail of yesterday's overnight slot.
        return day == today.minus(1) && crossesMidnight() && time.isBefore(closesAt);
    }

    public static boolean isOpenAt(Collection<OpeningSlot> slots, LocalDateTime localTime) {
        return slots.stream().anyMatch(slot -> slot.isOpenAt(localTime));
    }

    public DayOfWeek getDay() { return day; }
    public LocalTime getOpensAt() { return opensAt; }
    public LocalTime getClosesAt() { return closesAt; }
}
