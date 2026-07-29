package io.tokenpilot.core.domain;

import java.util.Objects;

public record PricingResolution(
        PricingResolutionStatus status
) {
    public PricingResolution {
        Objects.requireNonNull(status, "status must not be null");
    }

    public static PricingResolution resolved() {
        return new PricingResolution(PricingResolutionStatus.RESOLVED);
    }

    public static PricingResolution missingPlan() {
        return new PricingResolution(PricingResolutionStatus.MISSING_PLAN);
    }

    public static PricingResolution missingRate() {
        return new PricingResolution(PricingResolutionStatus.MISSING_RATE);
    }

    public static PricingResolution currencyMismatch() {
        return new PricingResolution(PricingResolutionStatus.CURRENCY_MISMATCH);
    }

    public boolean isResolved() {
        return status == PricingResolutionStatus.RESOLVED;
    }
}