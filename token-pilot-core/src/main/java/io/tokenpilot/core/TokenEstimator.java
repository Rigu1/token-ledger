package io.tokenpilot.core;

import io.tokenpilot.core.domain.TokenCountResult;

public interface TokenEstimator {
    TokenCountResult estimate(String text);
}
