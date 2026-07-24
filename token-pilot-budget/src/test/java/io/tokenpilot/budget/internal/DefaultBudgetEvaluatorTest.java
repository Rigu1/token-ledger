package io.tokenpilot.budget.internal;

import io.tokenpilot.budget.BudgetDecision;
import io.tokenpilot.budget.BudgetKey;
import io.tokenpilot.budget.BudgetPolicy;
import io.tokenpilot.budget.BudgetState;
import io.tokenpilot.budget.BudgetStateStore;
import io.tokenpilot.budget.BudgetThreshold;
import io.tokenpilot.budget.BudgetWindow;
import io.tokenpilot.budget.exception.BudgetExceededException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultBudgetEvaluatorTest {

  private static final Currency USD = Currency.getInstance("USD");
  private static final Map<String, String> TAGS = Map.of("tenant_id", "tenant-a");

  private BudgetStateStore store;

  @BeforeEach
  void setUp() {
    store = mock(BudgetStateStore.class);
  }

  @Test
  void 예상_비용을_포함해_임계치를_판정한다() {
    DefaultBudgetEvaluator evaluator = evaluator(policy(null, ZoneOffset.UTC), "2026-07-22T00:00:00Z");
    when(store.getAccumulatedCost(any(), any(), any())).thenReturn(new BigDecimal("70.00"));

    BudgetDecision result = evaluator.evaluate(TAGS, new BigDecimal("10.00"));

    assertThat(result.state()).isEqualTo(BudgetState.WARN);
    assertThat(result.threshold()).isEqualTo(BudgetThreshold.WARNING);
    assertThat(result.key()).isEqualTo(key("policy-a", "tenant-a", "2026-07"));
  }

  @Test
  void 예산_초과시_동일_key를_포함한_예외가_발생한다() {
    DefaultBudgetEvaluator evaluator = evaluator(policy(null, ZoneOffset.UTC), "2026-07-22T00:00:00Z");
    when(store.getAccumulatedCost(any(), any(), any())).thenReturn(new BigDecimal("95.00"));

    assertThatThrownBy(() -> evaluator.evaluate(TAGS, new BigDecimal("10.00")))
        .isInstanceOf(BudgetExceededException.class)
        .extracting(exception -> ((BudgetExceededException) exception).getDecision())
        .satisfies(decision -> {
          assertThat(decision.state()).isEqualTo(BudgetState.BLOCK);
          assertThat(decision.key()).isEqualTo(key("policy-a", "tenant-a", "2026-07"));
        });
  }

  @Test
  void target_identity_누락은_fail_closed다() {
    DefaultBudgetEvaluator evaluator = evaluator(policy(null, ZoneOffset.UTC), "2026-07-22T00:00:00Z");

    assertThatThrownBy(() -> evaluator.evaluate(Map.of("service", "chat")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("tenant_id");

    verify(store, never()).getAccumulatedCost(any(), any(), any());
  }

  @Test
  void 명시적_fallback이_설정된_경우에만_fallback_bucket을_사용한다() {
    DefaultBudgetEvaluator evaluator = evaluator(
        policy("shared-fallback", ZoneOffset.UTC),
        "2026-07-22T00:00:00Z"
    );
    when(store.getAccumulatedCost(any(), any(), any())).thenReturn(BigDecimal.ZERO);

    BudgetDecision decision = evaluator.evaluate(Map.of("service", "chat"));

    assertThat(decision.key().targetId()).isEqualTo("shared-fallback");
  }

  @ParameterizedTest
  @CsvSource({
      "2026-07-31T23:59:59Z, 2026-07",
      "2026-08-01T00:00:00Z, 2026-08",
      "2028-02-29T23:59:59Z, 2028-02",
      "2028-03-01T00:00:00Z, 2028-03",
      "2026-12-31T23:59:59Z, 2026-12",
      "2027-01-01T00:00:00Z, 2027-01"
  })
  void Clock_fixed로_월_경계를_결정한다(String instant, String expectedWindow) {
    DefaultBudgetEvaluator evaluator = evaluator(policy(null, ZoneOffset.UTC), instant);
    when(store.getAccumulatedCost(any(), any(), any())).thenReturn(BigDecimal.ZERO);

    BudgetDecision decision = evaluator.evaluate(TAGS);

    assertThat(decision.key().window()).isEqualTo(BudgetWindow.parse(expectedWindow));
  }

  @Test
  void UTC와_설정_ZoneId의_월이_다르면_설정_ZoneId를_따른다() {
    String instant = "2026-07-31T15:30:00Z";
    when(store.getAccumulatedCost(any(), any(), any())).thenReturn(BigDecimal.ZERO);

    BudgetDecision utc = evaluator(policy(null, ZoneOffset.UTC), instant).evaluate(TAGS);
    BudgetDecision seoul = evaluator(policy(null, ZoneId.of("Asia/Seoul")), instant).evaluate(TAGS);

    assertThat(utc.key().window()).isEqualTo(BudgetWindow.parse("2026-07"));
    assertThat(seoul.key().window()).isEqualTo(BudgetWindow.parse("2026-08"));
  }

  @Test
  void 동시_요청에서도_key_생성_결과가_결정적이다() throws Exception {
    InMemoryBudgetStateStore stateStore = new InMemoryBudgetStateStore();
    DefaultBudgetEvaluator evaluator = new DefaultBudgetEvaluator(
        stateStore,
        policy(null, ZoneOffset.UTC),
        Clock.fixed(Instant.parse("2026-07-22T00:00:00Z"), ZoneOffset.UTC)
    );
    Set<BudgetKey> keys = ConcurrentHashMap.newKeySet();
    List<Future<BudgetKey>> futures = new ArrayList<>();

    try (var executor = Executors.newFixedThreadPool(8)) {
      for (int index = 0; index < 200; index++) {
        futures.add(executor.submit(() -> evaluator.evaluate(TAGS).key()));
      }
      executor.shutdown();
      for (Future<BudgetKey> future : futures) {
        keys.add(future.get(5, TimeUnit.SECONDS));
      }
      assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }

    assertThat(keys).containsExactly(key("policy-a", "tenant-a", "2026-07"));
  }

  private DefaultBudgetEvaluator evaluator(BudgetPolicy policy, String instant) {
    return new DefaultBudgetEvaluator(
        store,
        policy,
        Clock.fixed(Instant.parse(instant), ZoneOffset.UTC)
    );
  }

  private static BudgetPolicy policy(String fallbackTargetId, ZoneId zoneId) {
    return new BudgetPolicy(
        "policy-a",
        "tenant",
        "tenant_id",
        fallbackTargetId,
        new BigDecimal("100.00"),
        USD,
        zoneId
    );
  }

  private static BudgetKey key(String policyId, String targetId, String window) {
    return new BudgetKey(policyId, "tenant", targetId, BudgetWindow.parse(window));
  }
}
