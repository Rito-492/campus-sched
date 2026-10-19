package com.campus.events.scheduling;

import java.time.Instant;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TimeIntervalTest {
    static Instant at(String time) { return Instant.parse("2030-10-20T" + time + ":00+08:00"); }

    @Test void adjacentIntervalsDoNotOverlapButSetupDoes() {
        var first = TimeInterval.occupancy(at("10:00"), at("11:00"), 0, 0);
        assertFalse(first.overlaps(TimeInterval.occupancy(at("11:00"), at("12:00"), 0, 0)));
        assertTrue(first.overlaps(TimeInterval.occupancy(at("11:00"), at("12:00"), 15, 0)));
    }

    @Test void validatesOpeningBoundaries() {
        var interval = TimeInterval.occupancy(at("08:30"), at("09:30"), 30, 30);
        assertEquals(at("08:00"), interval.start());
        assertEquals(at("10:00"), interval.end());
        assertTrue(interval.withinOpeningHours(LocalTime.of(8, 0), LocalTime.of(10, 0)));
        assertFalse(interval.withinOpeningHours(LocalTime.of(8, 1), LocalTime.of(22, 0)));
        assertFalse(interval.withinOpeningHours(LocalTime.of(8, 0), LocalTime.of(9, 59)));
        assertFalse(interval.withinOpeningHours(LocalTime.of(22, 0), LocalTime.of(8, 0)));
    }

    @Test void rejectsCrossDayIncludingBuffersAndUsesShanghaiDay() {
        assertThrows(IllegalArgumentException.class, () -> TimeInterval.occupancy(at("00:30"), at("01:30"), 31, 0));
        assertThrows(IllegalArgumentException.class, () -> TimeInterval.occupancy(at("22:30"), at("23:30"), 0, 30));
        assertDoesNotThrow(() -> TimeInterval.occupancy(at("07:30"), at("08:30"), 0, 0)); // 跨 UTC 日但不跨上海日
    }

    @Test void rejectsInvalidDurationPrecisionAndBuffers() {
        assertThrows(IllegalArgumentException.class, () -> TimeInterval.occupancy(at("10:00"), at("10:29"), 0, 0));
        assertThrows(IllegalArgumentException.class, () -> TimeInterval.occupancy(at("10:00"), at("16:01"), 0, 0));
        assertThrows(IllegalArgumentException.class, () -> TimeInterval.occupancy(at("10:00").plusSeconds(1), at("11:00"), 0, 0));
        assertThrows(IllegalArgumentException.class, () -> TimeInterval.occupancy(at("10:00"), at("11:00"), -1, 0));
        assertThrows(IllegalArgumentException.class, () -> TimeInterval.occupancy(at("10:00"), at("11:00"), 0, 121));
        assertThrows(IllegalArgumentException.class, () -> new TimeInterval(at("11:00"), at("10:00")));
        assertThrows(IllegalArgumentException.class, () -> new TimeInterval(at("11:00"), at("11:00")));
        assertDoesNotThrow(() -> TimeInterval.occupancy(at("10:00"), at("10:30"), 120, 120));
        assertDoesNotThrow(() -> TimeInterval.occupancy(at("10:00"), at("16:00"), 0, 0));
    }
}
