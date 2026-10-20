package com.campus.events.scheduling;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/** 内部候选代价；CandidateSearch复用此顺序与规范化设备表示。 */
final class CandidateRanking {
    static final List<Integer> OFFSETS_MINUTES = List.of(0, -30, 30, -60, 60, -90, 90, -120, 120);
    // UUID.compareTo 使用带符号 long；以规范字符串保证与 PostgreSQL UUID 顺序一致。
    static final Comparator<UUID> UUID_ORDER = Comparator.comparing(UUID::toString);

    record Key(int shiftMinutes, boolean venueChanged, int replacedLines,
               Instant startAt, UUID venueId, String equipmentJson) {
        Key {
            if (!OFFSETS_MINUTES.contains(shiftMinutes) || replacedLines < 0 || replacedLines > 1)
                throw new IllegalArgumentException("候选偏移或替换项数超出搜索范围");
            Objects.requireNonNull(startAt);
            Objects.requireNonNull(venueId);
            Objects.requireNonNull(equipmentJson);
        }
    }

    static final Comparator<Key> ORDER = Comparator.comparingInt((Key key) -> Math.abs(key.shiftMinutes()))
            .thenComparing(Key::venueChanged).thenComparingInt(Key::replacedLines)
            .thenComparing(Key::startAt).thenComparing(Key::venueId, UUID_ORDER).thenComparing(Key::equipmentJson);

    private CandidateRanking() {}

    static String canonicalEquipment(Map<UUID, Integer> equipment) {
        equipment.forEach((id, quantity) -> {
            Objects.requireNonNull(id);
            if (quantity == null || quantity <= 0) throw new IllegalArgumentException("设备数量必须为正整数");
        });
        return equipment.entrySet().stream().sorted(Map.Entry.comparingByKey(UUID_ORDER))
                .map(entry -> "{\"resourceId\":\"" + entry.getKey() + "\",\"quantity\":" + entry.getValue() + "}")
                .collect(Collectors.joining(",", "[", "]"));
    }
}
