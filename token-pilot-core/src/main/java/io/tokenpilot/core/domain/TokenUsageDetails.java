package io.tokenpilot.core.domain;

public record TokenUsageDetails(
        long cachedInputTokens,
        long reasoningOutputTokens,
        long cachedOutputTokens
) {
    public TokenUsageDetails{
        if (cachedInputTokens < 0) {
            throw new IllegalArgumentException("cachedInputTokens must be non-negative");
        }
        if (reasoningOutputTokens < 0) {
            throw new IllegalArgumentException("reasoningOutputTokens must be non-negative");
        }
        if (cachedOutputTokens < 0) {
            throw new IllegalArgumentException("cachedOutputTokens must be non-negative");
        }
    }
}
