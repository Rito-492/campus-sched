package com.campus.events.scheduling;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;

/** 按端点扫描真实同时用量；调用方负责读取有效预约、排除本活动及资源资格校验。 */
final class CapacitySweep {
    private CapacitySweep() {}

    record Load(TimeInterval interval, int quantity) {
        Load {
            Objects.requireNonNull(interval);
            if (quantity <= 0) throw new IllegalArgumentException("用量必须为正整数");
        }
    }

    record Shortage(TimeInterval interval, long required, long available, long faultQuantity) {}

    static List<Shortage> shortages(TimeInterval requested, int quantity, int stock,
                                    List<Load> reservations, List<Load> faults) {
        Objects.requireNonNull(requested);
        if (quantity <= 0 || stock < 0) throw new IllegalArgumentException("申请数量或库存无效");
        var events = new TreeMap<Instant, long[]>();
        events.put(requested.start(), new long[2]);
        events.put(requested.end(), new long[2]);
        add(events, requested, reservations, 0);
        add(events, requested, faults, 1);
        var result = new ArrayList<Shortage>();
        long used = 0, faulty = 0;
        Instant previous = null;
        for (var event : events.entrySet()) {
            if (previous != null && previous.isBefore(event.getKey())) {
                long available = Math.max(0L, (long) stock - faulty);
                long required = used + quantity;
                if (required > available)
                    result.add(new Shortage(new TimeInterval(previous, event.getKey()), required, available, faulty));
            }
            // 同刻的增减合并后才检查下一区间，端点相接不产生瞬时超额。
            used += event.getValue()[0];
            faulty += event.getValue()[1];
            previous = event.getKey();
        }
        return List.copyOf(result);
    }

    private static void add(TreeMap<Instant, long[]> events, TimeInterval requested, List<Load> loads, int column) {
        for (var load : loads) {
            if (!requested.overlaps(load.interval)) continue;
            Instant start = requested.start().isAfter(load.interval.start()) ? requested.start() : load.interval.start();
            Instant end = requested.end().isBefore(load.interval.end()) ? requested.end() : load.interval.end();
            events.computeIfAbsent(start, ignored -> new long[2])[column] += load.quantity;
            events.computeIfAbsent(end, ignored -> new long[2])[column] -= load.quantity;
        }
    }
}
