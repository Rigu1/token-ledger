package io.tokenledger.core.internal;

import io.tokenledger.core.CostCalculator;
import io.tokenledger.core.domain.Cost;
import io.tokenledger.core.domain.PricingPlan;
import io.tokenledger.core.domain.TokenType;
import io.tokenledger.core.domain.TokenUsage;

import java.math.BigDecimal;

import java.util.Map;

/**
 * 기본 비용 계산기 구현체. 각 {@link TokenType} 별 단가를 적용하여 정밀하게 계산합니다.
 */
class DefaultCostCalculator implements CostCalculator {

    @Override
    public Cost calculate(TokenUsage usage, PricingPlan plan) {
        BigDecimal totalCostValue = BigDecimal.ZERO;

        // 사용된 모든 토큰 타입에 대해 각각의 단가를 적용하여 합산
        for (Map.Entry<TokenType, Long> entry : usage.tokenCounts()
                                                     .entrySet()) {
            TokenType type = entry.getKey();
            Long count = entry.getValue();

            if (count > 0) {
                BigDecimal rate = plan.getRate(type);
                BigDecimal typeCost = rate
                        .multiply(BigDecimal.valueOf(count))
                        .movePointLeft(3);
                totalCostValue = totalCostValue.add(typeCost);
            }
        }

        return new Cost(totalCostValue, plan.currency());
    }
}
