package io.tokenpilot.core.exception;

import io.tokenpilot.core.domain.PricingResolution;

public class MissingPricingException extends RuntimeException {
    private final PricingResolution resolution;

    public MissingPricingException(PricingResolution resolution) {
        super(resolution.name());
        this.resolution = resolution;
    }

    public PricingResolution getResolution() {
        return resolution;
    }
}
