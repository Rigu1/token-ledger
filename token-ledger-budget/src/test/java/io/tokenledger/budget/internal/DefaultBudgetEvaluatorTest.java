package io.tokenledger.budget.internal;

import io.tokenledger.budget.*;
import io.tokenledger.budget.exception.BudgetExceededException;
import io.tokenledger.core.domain.Cost;
import java.util.Currency;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DefaultBudgetEvaluatorTest {

    private static final Currency USD = Currency.getInstance("USD");
    private static final Currency KRW = Currency.getInstance("KRW");

    private static Cost usd(String amount) {
        return Cost.of(new BigDecimal(amount), USD);
    }

    private static Cost krw(String amount) {
        return Cost.of(new BigDecimal(amount), KRW);
    }

    @Test
    void shouldReturnAllowWhenUsageIsBelow80Percent() {
        // given
        BudgetStateStore store = new InMemoryBudgetStateStore();
        BudgetEvaluator evaluator =
                new DefaultBudgetEvaluator(store, usd("100"));

        Map<String, String> tags = Map.of("tenant_id", "test");

        // when
        BudgetDecision decision =
                evaluator.evaluate(tags, usd("50"));

        // then
        assertEquals(BudgetState.ALLOW, decision.state());
        assertEquals(usd("50"), decision.currentUsage());
        assertEquals(usd("100"), decision.limit());
    }

    @Test
    void shouldReturnWarnWhenUsageExceeds80Percent() {
        // given
        BudgetStateStore store = new InMemoryBudgetStateStore();
        BudgetEvaluator evaluator =
                new DefaultBudgetEvaluator(store, usd("100"));

        Map<String, String> tags = Map.of("tenant_id", "test");

        // when
        BudgetDecision decision =
                evaluator.evaluate(tags, usd("85"));

        // then
        assertEquals(BudgetState.WARN, decision.state());
        assertEquals(usd("85"), decision.currentUsage());
        assertEquals(usd("100"), decision.limit());
    }

    @Test
    void shouldThrowExceptionWhenUsageExceedsLimit() {
        // given
        BudgetStateStore store = new InMemoryBudgetStateStore();
        BudgetEvaluator evaluator =
                new DefaultBudgetEvaluator(store, usd("100"));

        Map<String, String> tags = Map.of("tenant_id", "test");

        // when & then
        assertThrows(
                BudgetExceededException.class,
                () -> evaluator.evaluate(tags, usd("120"))
        );
    }

    @Test
    void shouldEvaluateCurrentStatusWithoutCostAmount() {
        // given
        BudgetStateStore store = new InMemoryBudgetStateStore();
        BudgetEvaluator evaluator =
                new DefaultBudgetEvaluator(store, usd("100"));

        Map<String, String> tags = Map.of("tenant_id", "test");
        store.addCost(tags, usd("90"));

        // when
        BudgetDecision decision = evaluator.evaluate(tags);

        // then
        assertEquals(BudgetState.WARN, decision.state());
        assertEquals(usd("90"), decision.currentUsage());
        assertEquals(usd("100"), decision.limit());
    }

    @Test
    void evaluateShouldBePureFunction() {
        // given
        BudgetStateStore store = new InMemoryBudgetStateStore();
        BudgetEvaluator evaluator =
                new DefaultBudgetEvaluator(store, usd("100"));

        Map<String, String> tags = Map.of("tenant_id", "test");

        // when
        evaluator.evaluate(tags, usd("50"));
        evaluator.evaluate(tags, usd("50"));

        // then
        // Still ALLOW because addCost was not called
        assertEquals(Cost.zero(USD), store.getAccumulatedCost(tags, USD));
        assertEquals(BudgetState.ALLOW, evaluator.evaluate(tags, usd("50")).state());
    }

    @Test
    void shouldReturnCurrencyMismatchWithoutChangingAccumulatedCost() {
        BudgetStateStore store = new InMemoryBudgetStateStore();
        BudgetEvaluator evaluator =
                new DefaultBudgetEvaluator(store, usd("100"));

        Map<String, String> tags = Map.of("tenant_id", "test");
        store.addCost(tags, usd("10"));
        Cost accumulatedBefore = store.getAccumulatedCost(tags, USD);

        BudgetDecision decision = evaluator.evaluate(tags, krw("50"));

        assertEquals(BudgetState.CURRENCY_MISMATCH, decision.state());
        assertEquals(accumulatedBefore, decision.currentUsage());
        assertEquals(usd("100"), decision.limit());
        assertEquals(accumulatedBefore, store.getAccumulatedCost(tags, USD));
    }
}
