package com.campus.events.scheduling;

import com.campus.events.contract.Alternative;

public record StrategyResult(String strategy, boolean feasible, Alternative choice,
                             int evaluatedCount, long elapsedNanos) {}
