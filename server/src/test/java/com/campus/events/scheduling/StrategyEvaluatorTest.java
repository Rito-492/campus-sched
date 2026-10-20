package com.campus.events.scheduling;

import com.campus.events.contract.*;
import com.campus.events.foundation.ApiException;
import com.campus.events.resource.ResourceRepository;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StrategyEvaluatorTest {
    static final Instant START = Instant.parse("2030-10-20T02:00:00Z");
    static ValidatorFactory validators;
    ResourceRepository resources;
    ConstraintChecker checker;
    CandidateSearch search;
    StrategyEvaluator evaluator;
    Map<UUID, ResourceView> data;
    @BeforeAll static void validators() { validators = Validation.buildDefaultValidatorFactory(); }
    @AfterAll static void close() { validators.close(); }
    @BeforeEach void setup() {
        resources = mock(ResourceRepository.class);
        data = new HashMap<>();
        when(resources.all()).thenAnswer(i -> List.copyOf(data.values()));
        when(resources.get(any())).thenAnswer(i -> data.get(i.getArgument(0)));
        checker = new ConstraintChecker(resources, validators.getValidator());
        search = new CandidateSearch(resources, checker);
        evaluator = new StrategyEvaluator(search);
    }
    UUID resource(int id, ResourceKind kind, boolean teacher, boolean student) {
        UUID uuid = new UUID(0, id);
        data.put(uuid, new ResourceView(teacher, student, kind, "测试资源", kind == ResourceKind.VENUE ? "VENUE" : "PROJECTOR",
                kind == ResourceKind.VENUE ? 80 : null, kind == ResourceKind.VENUE ? 1 : 2,
                "08:00", "22:00", uuid, START));
        return uuid;
    }
    Plan plan(UUID venue, List<EquipmentNeed> equipment) {
        return new Plan(START, START.plusSeconds(3600), 30, 30, venue, equipment);
    }
    @Test void identityMatrixAppliesToVenueAndEveryEquipmentLine() {
        for (ResourceKind kind : ResourceKind.values()) {
            for (boolean teacher : List.of(false, true)) for (boolean student : List.of(false, true)) {
                var venue = resource(1, ResourceKind.VENUE, true, true);
                var equipment = resource(2, ResourceKind.EQUIPMENT, true, true);
                resource(kind == ResourceKind.VENUE ? 1 : 2, kind, teacher, student);
                var plan = plan(venue, List.of(new EquipmentNeed(equipment, 1)));
                for (Identity identity : Identity.values()) {
                    if (identity == Identity.TEACHER ? teacher : student) assertTrue(checker.check(plan, 60, null, identity).feasible());
                    else assertEquals("RESOURCE_INELIGIBLE",
                            assertThrows(ApiException.class, () -> checker.check(plan, 60, null, identity)).code());
                }
            }
        }
    }
    @Test void rankedPrefersOriginalVenueWhileFirstFitUsesUuidOrder() {
        var other = resource(1, ResourceKind.VENUE, true, true);
        var original = resource(2, ResourceKind.VENUE, true, true);
        var results = evaluator.evaluateStrategies(plan(original, List.of()), 60, null, Identity.TEACHER);
        assertEquals(List.of("FIRST_FIT", "RANKED"), results.stream().map(StrategyResult::strategy).toList());
        assertEquals(other, results.getFirst().choice().plan().venueId());
        assertEquals(original, results.getLast().choice().plan().venueId());
        assertEquals(1, results.getFirst().evaluatedCount());
        assertEquals(18, results.getLast().evaluatedCount());
        assertTrue(results.stream().allMatch(r -> r.elapsedNanos() > 0 && r.feasible()));
    }
    @Test void filtersIneligibleOriginalAndUsesSameCategorySingleReplacement() {
        var venue = resource(1, ResourceKind.VENUE, true, true);
        resource(2, ResourceKind.VENUE, true, false);
        var original = resource(3, ResourceKind.EQUIPMENT, true, false);
        var replacement = resource(4, ResourceKind.EQUIPMENT, false, true);
        var result = search.search(plan(venue, List.of(new EquipmentNeed(original, 2))), 60, null, Identity.STUDENT, false);
        assertEquals(5, result.items().size());
        assertTrue(result.items().stream().allMatch(a -> a.plan().venueId().equals(venue)
                && a.plan().equipment().equals(List.of(new EquipmentNeed(replacement, 2))) && a.replacedEquipmentLines() == 1));
        assertEquals(0, result.items().getFirst().timeShiftMinutes());
        assertEquals(-30, result.items().get(1).timeShiftMinutes());
        assertEquals(30, result.items().get(2).timeShiftMinutes());
        assertEquals(9, result.evaluated());
    }
    @Test void exactBudgetAndTruncationAreStableAndDeduplicated() {
        for (int i = 1; i <= 40; i++) resource(i, ResourceKind.VENUE, true, true);
        var original = plan(new UUID(0, 40), List.of());
        var result = search.search(original, 60, null, Identity.TEACHER, false);
        assertEquals(300, result.evaluated());
        assertTrue(result.truncated());
        assertEquals(5, result.items().size());
        assertEquals(5, result.items().stream().map(Alternative::plan).distinct().count());
        assertEquals(original, result.items().getFirst().plan());
        assertEquals(result, search.search(original, 60, null, Identity.TEACHER, false));
    }
    @Test void noSolutionHasNullChoiceAndZeroCandidatesForIneligibleResources() {
        var venue = resource(1, ResourceKind.VENUE, false, false);
        var results = evaluator.evaluateStrategies(plan(venue, List.of()), 60, null, Identity.STUDENT);
        assertTrue(results.stream().noneMatch(StrategyResult::feasible));
        assertTrue(results.stream().allMatch(r -> r.choice() == null && r.evaluatedCount() == 0));
        resource(1, ResourceKind.VENUE, true, true);
        results = evaluator.evaluateStrategies(plan(venue, List.of()), 100, null, Identity.STUDENT);
        assertTrue(results.stream().allMatch(r -> !r.feasible() && r.choice() == null && r.evaluatedCount() == 9));
    }
    @Test void checksHoursCapacityAndInvalidInputs() {
        var venue = resource(1, ResourceKind.VENUE, true, true);
        var p = new Plan(Instant.parse("2030-10-20T00:00:00Z"), Instant.parse("2030-10-20T01:00:00Z"), 30, 0, venue, List.of());
        var result = checker.check(p, 100, null, Identity.TEACHER);
        assertEquals(Set.of(ConflictCode.OUTSIDE_HOURS, ConflictCode.VENUE_CAPACITY),
                new HashSet<>(result.conflicts().stream().map(Conflict::code).toList()));
        assertNull(result.conflicts().stream().filter(c -> c.code() == ConflictCode.OUTSIDE_HOURS).findFirst().orElseThrow().available());
        assertThrows(ApiException.class, () -> checker.check(new Plan(START, START.plusSeconds(3600), -1, 0, venue, List.of()), 60, null, Identity.TEACHER));
        assertThrows(ApiException.class, () -> checker.check(plan(venue, List.of(new EquipmentNeed(venue, 1))), 60, null, Identity.TEACHER));
        assertThrows(ApiException.class, () -> checker.check(plan(venue, List.of()), 0, null, Identity.TEACHER));
    }
}
