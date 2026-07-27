package io.tokenpilot.budget.internal;

import io.tokenpilot.budget.BudgetDecision;
import io.tokenpilot.budget.BudgetEvaluator;
import io.tokenpilot.budget.BudgetKey;
import io.tokenpilot.budget.BudgetPolicy;
import io.tokenpilot.budget.BudgetState;
import io.tokenpilot.budget.BudgetStateStore;
import io.tokenpilot.budget.BudgetThreshold;
import io.tokenpilot.budget.BudgetWindow;
import io.tokenpilot.budget.exception.BudgetExceededException;
import io.tokenpilot.core.domain.Cost;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Map;
import java.util.Objects;

public class DefaultBudgetEvaluator implements BudgetEvaluator {

  private final BudgetStateStore store;
  private final BudgetPolicy policy;
  private final Clock clock;

  public DefaultBudgetEvaluator(BudgetStateStore store, BudgetPolicy policy, Clock clock) {
    this.store = Objects.requireNonNull(store, "store must not be null");
    this.policy = Objects.requireNonNull(policy, "policy must not be null");
    this.clock = Objects.requireNonNull(clock, "clock must not be null");
  }

  @Override
  public BudgetDecision evaluate(Map<String, String> tags, Cost cost) {
    Objects.requireNonNull(cost, "cost must not be null");
    BudgetKey key = resolveKey(tags);
    Cost currentUsage = store.getAccumulatedCost(key, policy.monthlyLimit());
    if (!policy.monthlyLimit().currency().equals(cost.currency())) {
      return decision(
          key,
          BudgetState.CURRENCY_MISMATCH,
          BudgetThreshold.NONE,
          "예산 통화와 비용 통화가 일치하지 않습니다",
          currentUsage
      );
    }

    Cost usage = currentUsage.add(cost);
    BudgetDecision decision = decide(key, usage);
    if (decision.state() == BudgetState.BLOCK) {
      throw new BudgetExceededException(decision);
    }
    return decision;
  }

  @Override
  public BudgetDecision evaluate(Map<String, String> tags) {
    BudgetKey key = resolveKey(tags);
    Cost usage = store.getAccumulatedCost(key, policy.monthlyLimit());
    return decide(key, usage);
  }

  private BudgetKey resolveKey(Map<String, String> tags) {
    Objects.requireNonNull(tags, "tags must not be null");
    String targetId = tags.get(policy.targetTagKey());
    if (targetId == null || targetId.isBlank()) {
      targetId = policy.fallbackTargetId();
    }
    if (targetId == null) {
      throw new IllegalArgumentException(
          "Missing required budget target tag: " + policy.targetTagKey()
      );
    }
    return new BudgetKey(
        policy.id(),
        policy.targetType(),
        targetId,
        BudgetWindow.resolve(clock, policy.zoneId())
    );
  }

  private BudgetDecision decide(BudgetKey key, Cost usage) {
    Cost halfThreshold = threshold("0.5");
    Cost warningThreshold = threshold("0.8");

    if (usage.compareTo(policy.monthlyLimit()) >= 0) {
      return decision(key, BudgetState.BLOCK, BudgetThreshold.EXCEEDED, "월 예산을 초과했습니다", usage);
    }
    if (usage.compareTo(warningThreshold) >= 0) {
      return decision(key, BudgetState.WARN, BudgetThreshold.WARNING, "월 예산의 80% 이상 사용", usage);
    }
    if (usage.compareTo(halfThreshold) >= 0) {
      return decision(key, BudgetState.ALLOW, BudgetThreshold.HALF, "월 예산의 50% 이상 사용", usage);
    }
    return decision(key, BudgetState.ALLOW, BudgetThreshold.NONE, "정상 범위 사용", usage);
  }

  private BudgetDecision decision(
      BudgetKey key,
      BudgetState state,
      BudgetThreshold threshold,
      String reason,
      Cost usage
  ) {
    return new BudgetDecision(
        key,
        state,
        threshold,
        reason,
        usage,
        policy.monthlyLimit()
    );
  }

  private Cost threshold(String ratio) {
    return Cost.of(
        policy.monthlyLimit().value().multiply(new BigDecimal(ratio)),
        policy.monthlyLimit().currency()
    );
  }
}
