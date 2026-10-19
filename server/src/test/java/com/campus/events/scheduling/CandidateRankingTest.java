package com.campus.events.scheduling;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static com.campus.events.scheduling.TimeIntervalTest.at;
import static org.junit.jupiter.api.Assertions.*;

class CandidateRankingTest {
    private static final UUID LOW = UUID.fromString("00000000-0000-0000-0000-000000003101");
    private static final UUID HIGH = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");

    @Test void ordersAllSixCostFieldsAndIsStableAcrossInputOrder() {
        var expected = List.of(
                new CandidateRanking.Key(0, false, 0, at("10:00"), LOW, "[]"),
                new CandidateRanking.Key(0, false, 0, at("10:00"), LOW, "[{}]"),
                new CandidateRanking.Key(0, false, 0, at("10:00"), HIGH, "[]"),
                new CandidateRanking.Key(0, false, 1, at("10:00"), LOW, "[]"),
                new CandidateRanking.Key(0, true, 0, at("10:00"), LOW, "[]"),
                new CandidateRanking.Key(-30, false, 0, at("09:30"), LOW, "[]"),
                new CandidateRanking.Key(30, false, 0, at("10:30"), LOW, "[]"),
                new CandidateRanking.Key(-60, false, 0, at("09:00"), LOW, "[]"));
        var shuffled = new ArrayList<>(expected);
        for (int seed = 0; seed < 20; seed++) {
            Collections.shuffle(shuffled, new java.util.Random(seed));
            shuffled.sort(CandidateRanking.ORDER);
            assertEquals(expected, shuffled);
        }
    }

    @Test void canonicalEquipmentIgnoresInsertionOrderAndKeepsQuantities() {
        var first = new LinkedHashMap<UUID, Integer>();
        first.put(HIGH, 2);
        first.put(LOW, 1);
        var second = new LinkedHashMap<UUID, Integer>();
        second.put(LOW, 1);
        second.put(HIGH, 2);
        assertEquals(CandidateRanking.canonicalEquipment(first), CandidateRanking.canonicalEquipment(second));
        assertEquals("[{\"resourceId\":\"" + LOW + "\",\"quantity\":1},{\"resourceId\":\"" + HIGH + "\",\"quantity\":2}]", CandidateRanking.canonicalEquipment(first));
        assertEquals("[]", CandidateRanking.canonicalEquipment(Map.of()));
    }

    @Test void rejectsInvalidSearchCostsAndEquipmentQuantities() {
        assertThrows(IllegalArgumentException.class, () -> new CandidateRanking.Key(15, false, 0, at("10:00"), LOW, "[]"));
        assertThrows(IllegalArgumentException.class, () -> new CandidateRanking.Key(0, false, 2, at("10:00"), LOW, "[]"));
        assertThrows(IllegalArgumentException.class, () -> CandidateRanking.canonicalEquipment(Map.of(LOW, 0)));
    }
}
