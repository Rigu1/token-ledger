package io.tokenpilot.budget.internal;

import io.tokenpilot.budget.BudgetDecision;
import io.tokenpilot.budget.BudgetState;
import io.tokenpilot.budget.BudgetThreshold;
import io.tokenpilot.budget.BudgetStateStore;
import io.tokenpilot.budget.exception.BudgetExceededException;
import io.tokenpilot.core.domain.Cost;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DefaultBudgetEvaluatorTest {

    private BudgetStateStore store;
    private DefaultBudgetEvaluator evaluator;

    private static final Currency USD = Currency.getInstance("USD");
    private static final Cost MONTHLY_LIMIT = usd("100.00");
    private static final Map<String, String> TAGS = Map.of("service", "test");

    @BeforeEach
    void setUp() {
        store = mock(BudgetStateStore.class);
        evaluator = new DefaultBudgetEvaluator(store, MONTHLY_LIMIT);
    }

    @Test
    @DisplayName("사용량이 50퍼센트 미만이면 ALLOW/NONE이다")
    void shouldReturnAllowAndNoneWhenUsageIsBelow50Percent() {
        when(store.getAccumulatedCost(TAGS, USD))
                .thenReturn(usd("10.00"), usd("30.00"));

        BudgetDecision withCost = evaluator.evaluate(TAGS, usd("10.00"));
        BudgetDecision currentOnly = evaluator.evaluate(TAGS);

        assertDecision(withCost, BudgetState.ALLOW, BudgetThreshold.NONE);
        assertDecision(currentOnly, BudgetState.ALLOW, BudgetThreshold.NONE);
    }

    @Test
    @DisplayName("사용량이 50퍼센트 이상이면 ALLOW/HALF이다")
    void shouldReturnAllowAndHalfWhenUsageIsAtLeast50Percent() {
        when(store.getAccumulatedCost(TAGS, USD))
                .thenReturn(usd("40.00"), usd("50.00"));

        BudgetDecision withCost = evaluator.evaluate(TAGS, usd("10.00"));
        BudgetDecision currentOnly = evaluator.evaluate(TAGS);

        assertDecision(withCost, BudgetState.ALLOW, BudgetThreshold.HALF);
        assertDecision(currentOnly, BudgetState.ALLOW, BudgetThreshold.HALF);
    }

    @Test
    @DisplayName("사용량이 80퍼센트 이상이면 WARN/WARNING이다")
    void shouldReturnWarnAndWarningWhenUsageIsAtLeast80Percent() {
        when(store.getAccumulatedCost(TAGS, USD))
                .thenReturn(usd("70.00"), usd("80.00"));

        BudgetDecision withCost = evaluator.evaluate(TAGS, usd("10.00"));
        BudgetDecision currentOnly = evaluator.evaluate(TAGS);

        assertDecision(withCost, BudgetState.WARN, BudgetThreshold.WARNING);
        assertDecision(currentOnly, BudgetState.WARN, BudgetThreshold.WARNING);
    }

    @Test
    @DisplayName("사용량이 100퍼센트 이상이면 BLOCK/EXCEEDED이다")
    void shouldThrowBudgetExceededExceptionWhenUsageIsAtLeast100Percent() {
        when(store.getAccumulatedCost(TAGS, USD))
                .thenReturn(usd("95.00"), usd("100.00"));

        assertThatThrownBy(() -> evaluator.evaluate(TAGS, usd("10.00")))
                .isInstanceOf(BudgetExceededException.class)
                .extracting(e -> ((BudgetExceededException) e).getDecision())
                .satisfies(decision -> {
                    assertThat(decision.state()).isEqualTo(BudgetState.BLOCK);
                    assertThat(decision.threshold()).isEqualTo(BudgetThreshold.EXCEEDED);
                });

        BudgetDecision currentOnly = evaluator.evaluate(TAGS);

        assertDecision(currentOnly, BudgetState.BLOCK, BudgetThreshold.EXCEEDED);
    }

    private static Cost usd(String amount) {
        return Cost.of(new BigDecimal(amount), USD);
    }

    private static void assertDecision(
            BudgetDecision decision,
            BudgetState state,
            BudgetThreshold threshold
    ) {
        assertThat(decision.state()).isEqualTo(state);
        assertThat(decision.threshold()).isEqualTo(threshold);
    }
}
