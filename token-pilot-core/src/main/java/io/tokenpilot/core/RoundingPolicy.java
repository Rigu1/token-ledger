package io.tokenpilot.core;

import io.tokenpilot.core.domain.Cost;

import java.math.RoundingMode;
import java.util.Objects;

/**
 * 내부 정밀도를 보존한 비용을 외부 표시/청구 경계에서 반올림하는 정책입니다.
 */
public final class RoundingPolicy {

    public static final RoundingPolicy COST_BOUNDARY_ROUNDING =
            new RoundingPolicy(6, RoundingMode.HALF_UP);

    private final int scale;
    private final RoundingMode roundingMode;

    private RoundingPolicy(int scale, RoundingMode roundingMode) {
        this.scale = scale;
        this.roundingMode = roundingMode;
    }

    public Cost apply(Cost cost) {
        Objects.requireNonNull(cost, "cost must not be null");
        return Cost.of(
                cost.value().setScale(scale, roundingMode),
                cost.currency()
        );
    }
}
