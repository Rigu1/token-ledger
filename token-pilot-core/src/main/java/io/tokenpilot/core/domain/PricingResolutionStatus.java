package io.tokenpilot.core.domain;

public enum PricingResolutionStatus {
    RESOLVED,
    MISSING_PLAN,
    MISSING_RATE,
    CURRENCY_MISMATCH
}