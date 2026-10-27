package com.cyberstock.market;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.Arrays;

/**
 * Candle intervals from 1 minute to 1 month. Intraday buckets are aligned on UTC epoch
 * boundaries; daily/weekly/monthly buckets are aligned to the exchange's local calendar.
 */
public enum Interval {
    M1("1m", 60),
    M5("5m", 300),
    M15("15m", 900),
    M30("30m", 1800),
    H1("1h", 3600),
    H4("4h", 14400),
    D1("1d", 86400),
    W1("1w", 604800),
    MN1("1M", 2592000);

    private final String code;
    private final long seconds;

    Interval(String code, long seconds) {
        this.code = code;
        this.seconds = seconds;
    }

    public String code() {
        return code;
    }

    /** Approximate duration in seconds (exact for intraday). */
    public long seconds() {
        return seconds;
    }

    public boolean isCalendar() {
        return this == D1 || this == W1 || this == MN1;
    }

    public static Interval fromCode(String code) {
        return Arrays.stream(values())
                .filter(i -> i.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported interval: " + code));
    }

    /** Start of the bucket containing {@code ts}, in epoch seconds. */
    public long bucketStart(Instant ts, ZoneId zone) {
        if (!isCalendar()) {
            long epoch = ts.getEpochSecond();
            return epoch - Math.floorMod(epoch, seconds);
        }
        ZonedDateTime local = ts.atZone(zone).truncatedTo(ChronoUnit.DAYS);
        ZonedDateTime start = switch (this) {
            case W1 -> local.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MN1 -> local.withDayOfMonth(1);
            default -> local;
        };
        return start.toEpochSecond();
    }

    /** Bucket start {@code n} buckets before {@code bucketStart}. */
    public long minus(long bucketStart, int n, ZoneId zone) {
        if (!isCalendar()) {
            return bucketStart - n * seconds;
        }
        ZonedDateTime z = Instant.ofEpochSecond(bucketStart).atZone(zone);
        ZonedDateTime r = switch (this) {
            case W1 -> z.minusWeeks(n);
            case MN1 -> z.minusMonths(n);
            default -> z.minusDays(n);
        };
        return r.toEpochSecond();
    }
}
