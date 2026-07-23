package io.tokenledger.core;

import io.tokenledger.core.domain.Cost;
import java.math.RoundingMode;

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
        return Cost.of(
                cost.amount().setScale(scale, roundingMode),
                cost.currency()
        );
    }
}