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

    private static Cost usd(String amount) {
        return Cost.of(new BigDecimal(amount), USD);
    }

    @Test
    void should_return_allow_when_usage_is_below_80_percent() {
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
    void should_return_warn_when_usage_exceeds_80_percent() {
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
    void should_throw_exception_when_usage_exceeds_limit() {
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
    void should_evaluate_current_status_without_cost_amount() {
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
    void evaluate_should_be_pure_function() {
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
}
