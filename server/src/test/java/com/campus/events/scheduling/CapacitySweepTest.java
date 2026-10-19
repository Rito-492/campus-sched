package com.campus.events.scheduling;

import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static com.campus.events.scheduling.TimeIntervalTest.at;
import static org.junit.jupiter.api.Assertions.*;

class CapacitySweepTest {
    private static TimeInterval interval(String start, String end) { return new TimeInterval(at(start), at(end)); }
    private static CapacitySweep.Load load(String start, String end, int quantity) {
        return new CapacitySweep.Load(interval(start, end), quantity);
    }

    @Test void nonSimultaneousReservationsAreNotSummed() {
        assertTrue(CapacitySweep.shortages(interval("10:00", "12:00"), 1, 2,
                List.of(load("10:00", "11:00", 1), load("11:00", "12:00", 1)), List.of()).isEmpty());
    }

    @Test void reportsExactShortageAndClipsToRequestedInterval() {
        var result = CapacitySweep.shortages(interval("11:00", "13:00"), 1, 2,
                List.of(load("10:00", "12:00", 2)), List.of());
        assertEquals(List.of(new CapacitySweep.Shortage(interval("11:00", "12:00"), 3, 2, 0)), result);
    }

    @Test void overlappingFaultsClampAvailabilityToZero() {
        var result = CapacitySweep.shortages(interval("10:00", "12:00"), 1, 2, List.of(),
                List.of(load("09:00", "11:00", 2), load("10:30", "12:30", 2)));
        assertEquals(3, result.size());
        assertTrue(result.stream().allMatch(s -> s.available() == 0));
        assertEquals(4, result.get(1).faultQuantity());
    }

    @Test void ignoresOutsideLoadsAndDoesNotOverflowIntegerTotals() {
        assertTrue(CapacitySweep.shortages(interval("10:00", "11:00"), 1, 1,
                List.of(load("09:00", "10:00", 1), load("11:00", "12:00", 1)), List.of()).isEmpty());
        var result = CapacitySweep.shortages(interval("10:00", "11:00"), Integer.MAX_VALUE, Integer.MAX_VALUE,
                List.of(load("10:00", "11:00", Integer.MAX_VALUE)), List.of());
        assertEquals(4294967294L, result.getFirst().required());
    }

    @Test void validatesQuantities() {
        assertThrows(IllegalArgumentException.class, () -> load("10:00", "11:00", 0));
        assertThrows(IllegalArgumentException.class, () -> CapacitySweep.shortages(interval("10:00", "11:00"), 0, 2, List.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> CapacitySweep.shortages(interval("10:00", "11:00"), 1, -1, List.of(), List.of()));
    }

    @Test void agreesWithIndependentMinuteOracleOnRandomOverlaps() {
        var random = new Random(55230315L);
        var start = at("10:00");
        var requested = new TimeInterval(start, start.plusSeconds(3600));
        for (int trial = 0; trial < 200; trial++) {
            var reservations = new java.util.ArrayList<CapacitySweep.Load>();
            var faults = new java.util.ArrayList<CapacitySweep.Load>();
            for (int i = 0; i < 15; i++) {
                int offset = random.nextInt(90) - 15;
                var range = new TimeInterval(start.plusSeconds(offset * 60L), start.plusSeconds((offset + 1 + random.nextInt(40)) * 60L));
                (random.nextBoolean() ? reservations : faults).add(new CapacitySweep.Load(range, 1 + random.nextInt(3)));
            }
            var actual = CapacitySweep.shortages(requested, 2, 8, reservations, faults);
            for (int minute = 0; minute < 60; minute++) {
                var point = start.plusSeconds(minute * 60L + 30);
                long used = reservations.stream().filter(l -> !point.isBefore(l.interval().start()) && point.isBefore(l.interval().end())).mapToLong(CapacitySweep.Load::quantity).sum();
                long faulty = faults.stream().filter(l -> !point.isBefore(l.interval().start()) && point.isBefore(l.interval().end())).mapToLong(CapacitySweep.Load::quantity).sum();
                assertEquals(used + 2 > Math.max(0, 8 - faulty), actual.stream().anyMatch(s -> !point.isBefore(s.interval().start()) && point.isBefore(s.interval().end())), "扫描与逐分钟独立计算不一致");
            }
        }
    }
}
