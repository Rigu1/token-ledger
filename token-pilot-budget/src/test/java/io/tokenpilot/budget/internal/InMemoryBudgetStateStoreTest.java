package io.tokenpilot.budget.internal;

import io.tokenpilot.core.domain.Cost;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryBudgetStateStoreTest {

    private static final Currency USD = Currency.getInstance("USD");
    private static final Currency KRW = Currency.getInstance("KRW");

    @Test
    @DisplayName("같은 태그라도 통화가 다르면 누적 비용을 분리해야 한다")
    void shouldSeparateAccumulatedCostByCurrency() {
        InMemoryBudgetStateStore store = new InMemoryBudgetStateStore();
        Map<String, String> tags = Map.of("tenant_id", "test");

        store.addCost(tags, usd("10.00"));

        assertThat(store.getAccumulatedCost(tags, USD)).isEqualTo(usd("10.00"));
        assertThat(store.getAccumulatedCost(tags, KRW)).isEqualTo(Cost.zero(KRW));
    }

    private static Cost usd(String amount) {
        return Cost.of(new BigDecimal(amount), USD);
    }
}