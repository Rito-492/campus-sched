package com.campus.events.scheduling;

import com.campus.events.contract.Identity;
import com.campus.events.contract.Plan;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 内部评测入口；同一数据库快照、同一候选空间，FIRST_FIT仅提前停止。 */
@Component
public class StrategyEvaluator {
    private final CandidateSearch search;
    public StrategyEvaluator(CandidateSearch search) { this.search = search; }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<StrategyResult> evaluateStrategies(Plan plan, int attendees, UUID excludeActivityId, Identity applicantIdentity) {
        return List.of(evaluate("FIRST_FIT", plan, attendees, excludeActivityId, applicantIdentity, true),
                evaluate("RANKED", plan, attendees, excludeActivityId, applicantIdentity, false));
    }

    private StrategyResult evaluate(String strategy, Plan plan, int attendees, UUID excluded, Identity identity, boolean firstFit) {
        long start = System.nanoTime();
        var result = search.search(plan, attendees, excluded, identity, firstFit);
        var choice = result.items().isEmpty() ? null : result.items().getFirst();
        return new StrategyResult(strategy, choice != null, choice, result.evaluated(), System.nanoTime() - start);
    }
}
