package io.tokenpilot.core.domain;

import java.util.Optional;

enum PricingRateFallback {
    REASONING_TO_COMPLETION(TokenType.REASONING, TokenType.COMPLETION),
    CACHE_READ_PROMPT_TO_PROMPT(TokenType.CACHE_READ_PROMPT, TokenType.PROMPT),
    CACHE_CREATION_PROMPT_TO_PROMPT(TokenType.CACHE_CREATION_PROMPT, TokenType.PROMPT);

    private final TokenType tokenType;
    private final TokenType fallbackTokenType;

    PricingRateFallback(TokenType tokenType, TokenType fallbackTokenType) {
        this.tokenType = tokenType;
        this.fallbackTokenType = fallbackTokenType;
    }

    static Optional<TokenType> fallbackFor(TokenType tokenType) {
        for (PricingRateFallback fallback : values()) {
            if (fallback.tokenType == tokenType) {
                return Optional.of(fallback.fallbackTokenType);
            }
        }

        return Optional.empty();
    }
}
