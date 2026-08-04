package io.tokenpilot.core.domain;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Currency;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * 특정 모델의 가격 정책 정보.
 * {@link TokenType} 별로 1,000(1K) 토큰당 가격을 관리합니다.
 *
 * @param modelId  모델 식별자 (예: gpt-4o, claude-3-5-sonnet)
 * @param rates    1K 토큰 타입별 단가 (Map)
 * @param currency 통화 (기본값: USD)
 */
public record PricingPlan(
        String modelId,
        String pricingPolicyId,
        Map<TokenType, BigDecimal> rates,
        Currency currency
) {
    public static final String DEFAULT_PRICING_POLICY_ID = "default";

    public PricingPlan {
        if (pricingPolicyId == null || pricingPolicyId.isBlank()) {
            throw new IllegalArgumentException("pricingPolicyId must not be blank");
        }

        Objects.requireNonNull(rates, "rates must not be null");
        Map<TokenType, BigDecimal> copiedRates = new EnumMap<>(TokenType.class);
        copiedRates.putAll(rates);
        rates = Collections.unmodifiableMap(copiedRates);
        if (currency == null) {
            currency = Currency.getInstance("USD");
        }

        rates.values().forEach(v -> {
            if (v.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Price cannot be negative");
            }
        });
    }

    public PricingPlan(String modelId, Map<TokenType, BigDecimal> rates, Currency currency) {
        this(modelId, DEFAULT_PRICING_POLICY_ID, rates, currency);
    }

    /**
     * 기본 입력/출력 단가와 통화를 사용하는 {@link PricingPlan}을 생성합니다.
     */
    public PricingPlan(String modelId, BigDecimal promptPricePerK, BigDecimal completionPricePerK, Currency currency) {
        this(modelId, DEFAULT_PRICING_POLICY_ID, createRates(promptPricePerK, completionPricePerK), currency);
    }

    /**
     * 기본 입력/출력 단가와 pricing policy id, 통화를 사용하는 {@link PricingPlan}을 생성합니다.
     */
    public PricingPlan(String modelId, String pricingPolicyId, BigDecimal promptPricePerK, BigDecimal completionPricePerK, Currency currency) {
        this(modelId, pricingPolicyId, createRates(promptPricePerK, completionPricePerK), currency);
    }

    /**
     * 기본 입력/출력 단가만 사용하는 {@link PricingPlan}을 생성합니다. 기본 통화는 USD입니다.
     */
    public PricingPlan(String modelId, BigDecimal promptPricePerK, BigDecimal completionPricePerK) {
        this(modelId, promptPricePerK, completionPricePerK, Currency.getInstance("USD"));
    }

    private static Map<TokenType, BigDecimal> createRates(BigDecimal prompt, BigDecimal completion) {
        Map<TokenType, BigDecimal> rates = new EnumMap<>(TokenType.class);
        rates.put(TokenType.PROMPT, prompt);
        rates.put(TokenType.COMPLETION, completion);
        return rates;
    }

    /**
     * 기본 입력 단가를 반환합니다. (하위 호환성)
     */
    public BigDecimal promptPricePerK() {
        return getRate(TokenType.PROMPT);
    }

    /**
     * 기본 출력 단가를 반환합니다. (하위 호환성)
     */
    public BigDecimal completionPricePerK() {
        return getRate(TokenType.COMPLETION);
    }

    /**
     * 특정 토큰 타입의 단가를 가져옵니다. 없을 시 계층 구조에 따라 대체값을 반환합니다.
     * REASONING -> COMPLETION
     * CACHE_READ_PROMPT, CACHE_CREATION_PROMPT -> PROMPT
     */
    public BigDecimal getRate(TokenType type) {
        if (rates.containsKey(type)) {
            return rates.get(type);
        }

        return PricingRateFallback.fallbackFor(type)
                .map(fallbackType -> rates.getOrDefault(fallbackType, BigDecimal.ZERO))
                .orElse(BigDecimal.ZERO);
    }

    /**
     * 특정 토큰 타입의 가격 결정 결과를 반환합니다.
     * 명시적으로 등록된 0 rate는 {@link PricingResolution#RESOLVED}로,
     * 누락된 rate는 {@link PricingResolution#MISSING_RATE}로 표현합니다.
     */
    public PricingResolution resolveRate(TokenType type) {
        if (rates.containsKey(type)) {
            return PricingResolution.RESOLVED;
        }

        return PricingRateFallback.fallbackFor(type)
                .filter(rates::containsKey)
                .map(fallbackType -> PricingResolution.RESOLVED)
                .orElse(PricingResolution.MISSING_RATE);
    }
}
