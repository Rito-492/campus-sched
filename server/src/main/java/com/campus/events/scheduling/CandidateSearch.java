package com.campus.events.scheduling;

import com.campus.events.contract.*;
import com.campus.events.foundation.ApiException;
import com.campus.events.resource.ResourceRepository;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** 两种策略共享偏移、资源顺序、资格过滤、硬约束与300个完整组合预算。 */
@Component
public class CandidateSearch {
    static final int MAX_EVALUATIONS = 300;
    private final ResourceRepository resources;
    private final ConstraintChecker checker;
    public CandidateSearch(ResourceRepository resources, ConstraintChecker checker) {
        this.resources = resources;
        this.checker = checker;
    }

    record SearchResult(List<Alternative> items, int evaluated, boolean truncated) {}

    SearchResult search(Plan original, int attendees, UUID excluded, Identity identity, boolean firstFit) {
        checker.validate(original, attendees, identity);
        var all = resources.all();
        var byId = all.stream().collect(Collectors.toMap(ResourceView::id, r -> r));
        for (UUID id : ConstraintChecker.needs(original).keySet())
            if (!byId.containsKey(id)) throw ApiException.notFound();
        if (byId.get(original.venueId()).kind() != ResourceKind.VENUE
                || original.equipment().stream().anyMatch(e -> byId.get(e.resourceId()).kind() != ResourceKind.EQUIPMENT))
            throw ApiException.invalid("计划中的资源类型不正确");
        var venues = all.stream().filter(r -> r.kind() == ResourceKind.VENUE && ConstraintChecker.eligible(r, identity))
                .sorted(Comparator.comparing(ResourceView::id, CandidateRanking.UUID_ORDER)).toList();
        var equipment = original.equipment().stream().sorted(Comparator.comparing(EquipmentNeed::resourceId, CandidateRanking.UUID_ORDER)).toList();
        var variants = new ArrayList<List<EquipmentNeed>>();
        variants.add(equipment);
        for (int line = 0; line < equipment.size(); line++) {
            var need = equipment.get(line);
            for (var replacement : all.stream().sorted(Comparator.comparing(ResourceView::id, CandidateRanking.UUID_ORDER)).toList()) {
                if (replacement.kind() != ResourceKind.EQUIPMENT
                        || !replacement.category().equals(byId.get(need.resourceId()).category())
                        || equipment.stream().anyMatch(e -> e.resourceId().equals(replacement.id()))) continue;
                var variant = new ArrayList<>(equipment);
                variant.set(line, new EquipmentNeed(replacement.id(), need.quantity()));
                variants.add(variant.stream().sorted(Comparator.comparing(EquipmentNeed::resourceId, CandidateRanking.UUID_ORDER)).toList());
            }
        }
        variants.removeIf(v -> v.stream().anyMatch(e -> !ConstraintChecker.eligible(byId.get(e.resourceId()), identity)));
        int evaluated = 0;
        boolean truncated = false;
        var feasible = new ArrayList<Alternative>();
        var seen = new HashSet<Plan>();
        outer:
        for (int offset : CandidateRanking.OFFSETS_MINUTES) {
            for (var venue : venues) {
                for (var variant : variants) {
                    var candidate = new Plan(original.startAt().plusSeconds(offset * 60L), original.endAt().plusSeconds(offset * 60L),
                            original.setupMinutes(), original.teardownMinutes(), venue.id(), variant);
                    if (!seen.add(candidate)) continue;
                    if (evaluated == MAX_EVALUATIONS) { truncated = true; break outer; }
                    evaluated++;
                    // 偏移可以跨出自然日；此完整组合计入预算，但不可作为方案。
                    if (!candidate.isValidPlan()) continue;
                    if (checker.check(candidate, attendees, excluded, identity).feasible()) {
                        int replacements = variant.equals(equipment) ? 0 : 1;
                        feasible.add(new Alternative(candidate, offset, !venue.id().equals(original.venueId()), replacements,
                                "在±120分钟、同类设备单项替换范围内通过资格及容量检查；确认时需重新检查"));
                        if (firstFit) return new SearchResult(List.copyOf(feasible), evaluated, false);
                    }
                }
            }
        }
        feasible.sort(Comparator.comparing(CandidateSearch::key, CandidateRanking.ORDER));
        return new SearchResult(feasible.stream().limit(5).toList(), evaluated, truncated);
    }

    private static CandidateRanking.Key key(Alternative a) {
        var equipment = a.plan().equipment().stream().collect(Collectors.toMap(EquipmentNeed::resourceId, EquipmentNeed::quantity));
        return new CandidateRanking.Key(a.timeShiftMinutes(), a.venueChanged(), a.replacedEquipmentLines(),
                a.plan().startAt(), a.plan().venueId(), CandidateRanking.canonicalEquipment(equipment));
    }
}
