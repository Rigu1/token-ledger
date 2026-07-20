package io.tokenpilot.core.domain;

public record TokenUsageDetails(
        long cachedInputTokens,
        long reasoningOutputTokens,
        long cachedOutputTokens
) {}
