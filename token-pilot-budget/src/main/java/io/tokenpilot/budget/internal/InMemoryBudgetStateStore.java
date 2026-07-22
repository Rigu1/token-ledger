package io.tokenpilot.budget.internal;

import io.tokenpilot.budget.BudgetKey;
import io.tokenpilot.budget.BudgetStateStore;
import io.tokenpilot.core.domain.Cost;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * resolved {@link BudgetKey}별 비용을 관리하는 인메모리 저장소입니다.
 */
public class InMemoryBudgetStateStore implements BudgetStateStore {

  private final ConcurrentMap<BudgetKey, Bucket> store = new ConcurrentHashMap<>();

  @Override
  public BigDecimal getAccumulatedCost(BudgetKey key, BigDecimal limit, Currency currency) {
    validateArguments(key, limit, currency);
    Bucket bucket = store.computeIfAbsent(
        key,
        ignored -> new Bucket(limit, currency, BigDecimal.ZERO)
    );
    bucket.validate(limit, currency);
    return bucket.accumulatedCost();
  }

  @Override
  public void addCost(BudgetKey key, BigDecimal limit, Currency currency, Cost amount) {
    validateArguments(key, limit, currency);
    Objects.requireNonNull(amount, "amount must not be null");
    if (!currency.equals(amount.currency())) {
      throw new IllegalArgumentException("Budget currency does not match cost currency");
    }

    store.compute(key, (ignored, bucket) -> {
      if (bucket == null) {
        return new Bucket(limit, currency, amount.value());
      }
      bucket.validate(limit, currency);
      return new Bucket(limit, currency, bucket.accumulatedCost().add(amount.value()));
    });
  }

  private void validateArguments(BudgetKey key, BigDecimal limit, Currency currency) {
    Objects.requireNonNull(key, "key must not be null");
    Objects.requireNonNull(limit, "limit must not be null");
    Objects.requireNonNull(currency, "currency must not be null");
  }

  private record Bucket(BigDecimal limit, Currency currency, BigDecimal accumulatedCost) {

    private void validate(BigDecimal expectedLimit, Currency expectedCurrency) {
      if (limit.compareTo(expectedLimit) != 0 || !currency.equals(expectedCurrency)) {
        throw new IllegalArgumentException("Budget policy snapshot changed for an existing key");
      }
    }
  }
}
