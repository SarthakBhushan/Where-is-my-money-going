package com.whereismymoneygoing.backend.nlp;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;

/** A date range the question refers to. */
public record Period(Kind kind, LocalDate from, LocalDate to, int months) {

    public enum Kind { MONTH, DAY, WEEK, LAST_N_MONTHS, ALL }

    public static Period month(YearMonth ym) {
        return new Period(Kind.MONTH, ym.atDay(1), ym.atEndOfMonth(), 1);
    }

    public static Period day(LocalDate d) {
        return new Period(Kind.DAY, d, d, 0);
    }

    /** Monday of the week containing {@code d} up to {@code d} (or the following Sunday when {@code full}). */
    public static Period weekOf(LocalDate d, boolean full) {
        LocalDate monday = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return new Period(Kind.WEEK, monday, full ? monday.plusDays(6) : d, 0);
    }

    public static Period lastMonths(int n, LocalDate anchor) {
        YearMonth start = YearMonth.from(anchor).minusMonths(n - 1L);
        return new Period(Kind.LAST_N_MONTHS, start.atDay(1), anchor, n);
    }

    public static Period all(LocalDate from, LocalDate to) {
        return new Period(Kind.ALL, from, to, 0);
    }

    public boolean contains(LocalDate d) {
        return !d.isBefore(from) && !d.isAfter(to);
    }

    public YearMonth yearMonth() {
        return YearMonth.from(from);
    }

    /** The period right before this one, or null when there is no sensible one (whole statement). */
    public Period previous() {
        return switch (kind) {
            case MONTH -> month(yearMonth().minusMonths(1));
            case DAY -> day(from.minusDays(1));
            case WEEK -> new Period(Kind.WEEK, from.minusDays(7), from.minusDays(1), 0);
            case LAST_N_MONTHS -> {
                YearMonth start = yearMonth().minusMonths(months);
                yield new Period(Kind.LAST_N_MONTHS, start.atDay(1),
                        yearMonth().minusMonths(1).atEndOfMonth(), months);
            }
            case ALL -> null;
        };
    }
}
