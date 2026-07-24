package io.tokenpilot.sample;

import io.tokenpilot.core.RoundingPolicy;
import io.tokenpilot.core.domain.Cost;

final class CostBoundaryFormatter {

    private CostBoundaryFormatter() {
    }

    static String format(Cost cost) {
        Cost rounded = RoundingPolicy.COST_BOUNDARY_ROUNDING.apply(cost);
        return rounded.value().toPlainString();
    }
}
