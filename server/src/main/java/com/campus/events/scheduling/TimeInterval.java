package com.campus.events.scheduling;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/** 调度内部半开区间；不是共享 Plan DTO。 */
record TimeInterval(Instant start, Instant end) {
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    TimeInterval {
        Objects.requireNonNull(start);
        Objects.requireNonNull(end);
        if (!start.isBefore(end)) throw new IllegalArgumentException("区间起点必须早于终点");
    }

    static TimeInterval occupancy(Instant start, Instant end, int setup, int teardown) {
        var event = new TimeInterval(start, end);
        if (!start.equals(start.truncatedTo(ChronoUnit.MINUTES))
                || !end.equals(end.truncatedTo(ChronoUnit.MINUTES)))
            throw new IllegalArgumentException("活动时间必须精确到分钟");
        long minutes = Duration.between(event.start, event.end).toMinutes();
        if (minutes < 30 || minutes > 360 || setup < 0 || setup > 120 || teardown < 0 || teardown > 120)
            throw new IllegalArgumentException("活动时长或布撤场超出范围");
        var occupied = new TimeInterval(start.minus(setup, ChronoUnit.MINUTES), end.plus(teardown, ChronoUnit.MINUTES));
        if (!occupied.start.atZone(SHANGHAI).toLocalDate().equals(occupied.end.atZone(SHANGHAI).toLocalDate()))
            throw new IllegalArgumentException("占用必须在同一上海自然日内");
        return occupied;
    }

    boolean overlaps(TimeInterval other) {
        return start.isBefore(other.end) && other.start.isBefore(end);
    }

    boolean withinOpeningHours(LocalTime from, LocalTime to) {
        Objects.requireNonNull(from);
        Objects.requireNonNull(to);
        var localStart = start.atZone(SHANGHAI);
        var localEnd = end.atZone(SHANGHAI);
        return from.isBefore(to) && localStart.toLocalDate().equals(localEnd.toLocalDate())
                && !localStart.toLocalTime().isBefore(from) && !localEnd.toLocalTime().isAfter(to);
    }
}
