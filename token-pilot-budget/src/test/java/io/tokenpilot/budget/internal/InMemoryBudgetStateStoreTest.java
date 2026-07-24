package io.tokenpilot.budget.internal;

import io.tokenpilot.budget.BudgetKey;
import io.tokenpilot.budget.BudgetWindow;
import io.tokenpilot.core.domain.Cost;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryBudgetStateStoreTest {

  private static final Currency USD = Currency.getInstance("USD");
  private static final BigDecimal LIMIT = new BigDecimal("100.00");

  @Test
  void 같은_policy_target_window는_계속_누적된다() {
    InMemoryBudgetStateStore store = new InMemoryBudgetStateStore();
    BudgetKey key = key("policy-a", "tenant-a", "2026-07");

    add(store, key, "10.00", USD);
    add(store, key, "5.00", USD);

    assertThat(store.getAccumulatedCost(key, LIMIT, USD)).isEqualByComparingTo("15.00");
  }

  @Test
  void 다음_월과_다른_target_policy는_각각_격리된다() {
    InMemoryBudgetStateStore store = new InMemoryBudgetStateStore();
    BudgetKey july = key("policy-a", "tenant-a", "2026-07");
    BudgetKey august = key("policy-a", "tenant-a", "2026-08");
    BudgetKey otherTarget = key("policy-a", "tenant-b", "2026-07");
    BudgetKey otherPolicy = key("policy-b", "tenant-a", "2026-07");

    add(store, july, "10.00", USD);
    add(store, otherTarget, "20.00", USD);
    add(store, otherPolicy, "30.00", USD);

    assertThat(store.getAccumulatedCost(july, LIMIT, USD)).isEqualByComparingTo("10.00");
    assertThat(store.getAccumulatedCost(august, LIMIT, USD)).isEqualByComparingTo("0");
    assertThat(store.getAccumulatedCost(otherTarget, LIMIT, USD)).isEqualByComparingTo("20.00");
    assertThat(store.getAccumulatedCost(otherPolicy, LIMIT, USD)).isEqualByComparingTo("30.00");
  }

  @Test
  void currency_mismatch는_상태를_변경하지_않고_실패한다() {
    InMemoryBudgetStateStore store = new InMemoryBudgetStateStore();
    BudgetKey key = key("policy-a", "tenant-a", "2026-07");
    add(store, key, "10.00", USD);

    assertThatThrownBy(() -> store.addCost(
        key,
        LIMIT,
        USD,
        new Cost(new BigDecimal("1000"), Currency.getInstance("KRW"))
    )).isInstanceOf(IllegalArgumentException.class);

    assertThat(store.getAccumulatedCost(key, LIMIT, USD)).isEqualByComparingTo("10.00");
  }

  @Test
  void 최초_조회에서_limit과_currency_snapshot을_고정한다() {
    InMemoryBudgetStateStore store = new InMemoryBudgetStateStore();
    BudgetKey key = key("policy-a", "tenant-a", "2026-07");

    assertThat(store.getAccumulatedCost(key, LIMIT, USD)).isEqualByComparingTo("0");

    assertThatThrownBy(() -> store.addCost(
        key,
        new BigDecimal("200.00"),
        USD,
        new Cost(BigDecimal.ONE, USD)
    )).isInstanceOf(IllegalArgumentException.class);

    Currency krw = Currency.getInstance("KRW");
    assertThatThrownBy(() -> store.addCost(
        key,
        LIMIT,
        krw,
        new Cost(BigDecimal.ONE, krw)
    )).isInstanceOf(IllegalArgumentException.class);

    assertThat(store.getAccumulatedCost(key, LIMIT, USD)).isEqualByComparingTo("0");
  }

  private static void add(
      InMemoryBudgetStateStore store,
      BudgetKey key,
      String amount,
      Currency currency
  ) {
    store.addCost(key, LIMIT, currency, new Cost(new BigDecimal(amount), currency));
  }

  private static BudgetKey key(String policyId, String targetId, String window) {
    return new BudgetKey(policyId, "tenant", targetId, BudgetWindow.parse(window));
  }
}
