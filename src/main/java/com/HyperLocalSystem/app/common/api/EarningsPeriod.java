package com.HyperLocalSystem.app.common.api;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

/** The {@code period} of both earnings endpoints (partner and merchant), in Indian time. */
public enum EarningsPeriod {
    TODAY,
    /** From Monday. */
    WEEK,
    /** From the 1st. */
    MONTH;

    public static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");

    public LocalDate firstDay(LocalDate today) {
        return switch (this) {
            case TODAY -> today;
            case WEEK -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTH -> today.withDayOfMonth(1);
        };
    }

    public static Instant startOf(LocalDate day) {
        return day.atStartOfDay(INDIA).toInstant();
    }
}
