package io.tokenpilot.core.internal;

import io.tokenpilot.core.CostCalculator;
import io.tokenpilot.core.domain.Cost;
import io.tokenpilot.core.domain.PricingPlan;
import io.tokenpilot.core.domain.TokenType;
import io.tokenpilot.core.domain.TokenUsage;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 기본 비용 계산기 구현체.
 * 포괄 총량에서 cache/reasoning 세부량을 분리한 배타적 구간별로
 * {@link TokenType} 단가를 적용하여 중복 없이 계산합니다.
 * 1K 토큰당 가격 정보를 사용하여 소수점 10자리까지 중간 계산 후 6자리로 최종 반올림합니다.
 */
class DefaultCostCalculator implements CostCalculator {
    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000);

    @Override
    public Cost calculate(TokenUsage usage, PricingPlan plan) {
        long cacheReadInput = countOrZero(usage.details().cacheReadInputTokens());
        long cacheCreationInput = countOrZero(usage.details().cacheCreationInputTokens());
        long reasoningOutput = countOrZero(usage.details().reasoningOutputTokens());

        long regularInput = usage.inputTokens() - cacheReadInput - cacheCreationInput;
        long regularOutput = usage.outputTokens() - reasoningOutput;

        BigDecimal totalCostValue = costFor(regularInput, plan.getRate(TokenType.PROMPT))
                .add(costFor(cacheReadInput, plan.getRate(TokenType.CACHE_READ_PROMPT)))
                .add(costFor(cacheCreationInput, plan.getRate(TokenType.CACHE_CREATION_PROMPT)))
                .add(costFor(regularOutput, plan.getRate(TokenType.COMPLETION)))
                .add(costFor(reasoningOutput, plan.getRate(TokenType.REASONING)));

        return new Cost(totalCostValue, plan.currency());
    }

    private BigDecimal costFor(long count, BigDecimal rate) {
        if (count == 0) {
            return BigDecimal.ZERO;
        }

        return rate.multiply(BigDecimal.valueOf(count))
                .divide(THOUSAND, 10, RoundingMode.HALF_UP);
    }

    private long countOrZero(Long count) {
        return count == null ? 0L : count;
    }
}
