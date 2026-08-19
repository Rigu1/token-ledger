package io.tokenpilot.core;

import io.tokenpilot.core.domain.Cost;
import io.tokenpilot.core.domain.PricingPlan;
import io.tokenpilot.core.domain.PricingSnapshot;
import io.tokenpilot.core.domain.TokenUsage;

import java.util.Objects;

/**
 * 사용량과 가격 정책을 바탕으로 비용을 계산하는 인터페이스.
 */
public interface CostCalculator {
    /**
     * 토큰 사용량과 가격 정책을 기반으로 비용을 산출합니다.
     * @param usage 토큰 사용량
     * @param plan  가격 정책
     * @return 산출된 비용
     */
    Cost calculate(TokenUsage usage, PricingPlan plan);

    /**
     * 예약 시점에 고정한 가격 snapshot으로 비용을 계산합니다.
     */
    default Cost calculate(TokenUsage usage, PricingSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        return calculate(
                usage,
                new PricingPlan(
                        snapshot.modelId(),
                        snapshot.pricingPolicyId(),
                        snapshot.rates(),
                        snapshot.currency()
                )
        );
    }
}
