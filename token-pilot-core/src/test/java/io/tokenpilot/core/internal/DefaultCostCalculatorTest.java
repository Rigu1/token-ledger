package io.tokenpilot.core.internal;

import io.tokenpilot.core.domain.Cost;
import io.tokenpilot.core.domain.PricingPlan;
import io.tokenpilot.core.domain.TokenUsage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultCostCalculatorTest {

    private final DefaultCostCalculator calculator = new DefaultCostCalculator();

    @Test
    @DisplayName("기본 입력/출력 토큰 비용이 정확하게 계산되어야 한다")
    void calculateStandardTokens() {
        // Given: Input $0.01/1k, Output $0.03/1k
        PricingPlan plan = new PricingPlan("gpt-4o", new BigDecimal("0.01"), new BigDecimal("0.03"));
        // Usage: Input 1000, Output 2000
        TokenUsage usage = TokenUsage.from(1000, 2000);

        // When
        Cost cost = calculator.calculate(usage, plan);

        // Then: (1000 * 0.01 / 1000) + (2000 * 0.03 / 1000) = 0.01 + 0.06 = 0.07
        assertThat(cost.value()).isEqualByComparingTo("0.070000");
    }
}
