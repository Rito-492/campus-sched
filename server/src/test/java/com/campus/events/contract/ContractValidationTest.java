package com.campus.events.contract;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ContractValidationTest {
    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();
    private final UUID venue = UUID.randomUUID(), equipment = UUID.randomUUID();
    private Plan plan(String start, String end, int setup, List<EquipmentNeed> needs) {
        return new Plan(Instant.parse(start), Instant.parse(end), setup, 0, venue, needs);
    }
    @Test void legalPlanAndAllEligibilityCombinationsAreAccepted() {
        assertTrue(VALIDATOR.validate(plan("2030-10-20T02:00:00Z", "2030-10-20T03:00:00Z", 30, List.of(new EquipmentNeed(equipment, 2)))).isEmpty());
        for (boolean teacher : List.of(false, true)) for (boolean student : List.of(false, true))
            assertTrue(VALIDATOR.validate(new ResourceEligibility(teacher, student)).isEmpty());
    }
    @Test void durationMinutePrecisionAndShanghaiDayAreChecked() {
        assertFalse(VALIDATOR.validate(plan("2030-10-20T02:00:01Z", "2030-10-20T03:00:00Z", 0, List.of())).isEmpty());
        assertFalse(VALIDATOR.validate(plan("2030-10-20T02:00:00Z", "2030-10-20T02:29:00Z", 0, List.of())).isEmpty());
        assertFalse(VALIDATOR.validate(plan("2030-10-20T02:00:00Z", "2030-10-20T09:00:00Z", 0, List.of())).isEmpty());
        assertFalse(VALIDATOR.validate(plan("2030-10-20T16:00:00Z", "2030-10-20T17:00:00Z", 30, List.of())).isEmpty());
    }
    @Test void duplicateResourcesAndInvalidQuantityFail() {
        assertFalse(VALIDATOR.validate(plan("2030-10-20T02:00:00Z", "2030-10-20T03:00:00Z", 0,
                List.of(new EquipmentNeed(equipment, 2), new EquipmentNeed(equipment, 1)))).isEmpty());
        assertFalse(VALIDATOR.validate(new EquipmentNeed(equipment, 0)).isEmpty());
    }
    @Test void rejectionRequiresReason() {
        assertFalse(VALIDATOR.validate(new ChangeDecision(Decision.REJECT, "  ")).isEmpty());
        assertTrue(VALIDATOR.validate(new ChangeDecision(Decision.APPROVE, "")).isEmpty());
        assertFalse(VALIDATOR.validate(new ReasonCommand(0, "")).isEmpty());
    }
    @Test void resourceShapeAndOpenWindowAreChecked() {
        assertTrue(VALIDATOR.validate(new ResourceCreate(true, false, ResourceKind.VENUE, "教室", "VENUE", 80, 1, "08:00", "22:00")).isEmpty());
        assertFalse(VALIDATOR.validate(new ResourceCreate(true, true, ResourceKind.VENUE, "教室", "VENUE", null, 2, "08:00", "22:00")).isEmpty());
        assertFalse(VALIDATOR.validate(new ResourceCreate(true, true, ResourceKind.EQUIPMENT, "器材", "PROJECTOR", null, 2, "22:00", "08:00")).isEmpty());
    }
    @Test void expectedAttendeesMustCoverCapacity() {
        var p = plan("2030-10-20T02:00:00Z", "2030-10-20T03:00:00Z", 0, List.of());
        assertFalse(VALIDATOR.validate(new ActivityInput("测试", "", 1, 2, List.of(), p)).isEmpty());
        assertTrue(VALIDATOR.validate(new ActivityInput("测试", "", 2, 1, List.of(), p)).isEmpty());
    }
}
