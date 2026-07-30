package io.tokenpilot.core.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.Currency;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * provider 호출 전에 확정된 요청 단위 pricing snapshot.
 */
public record PricingSnapshot(
        String modelId,
        String pricingPolicyId,
        String catalogVersion,
        Instant checkedAt,
        Map<TokenType, BigDecimal> rates,
        Currency currency
) {
    public static final String DEFAULT_CATALOG_VERSION = "default";

    public PricingSnapshot {
        if (modelId == null || modelId.isBlank()) {
            throw new IllegalArgumentException("modelId must not be blank");
        }
        if (pricingPolicyId == null || pricingPolicyId.isBlank()) {
            throw new IllegalArgumentException("pricingPolicyId must not be blank");
        }
        if (catalogVersion == null || catalogVersion.isBlank()) {
            throw new IllegalArgumentException("catalogVersion must not be blank");
        }

        checkedAt = Objects.requireNonNull(checkedAt, "checkedAt must not be null");
        currency = Objects.requireNonNull(currency, "currency must not be null");
        Objects.requireNonNull(rates, "rates must not be null");
        Map<TokenType, BigDecimal> copiedRates = new EnumMap<>(TokenType.class);
        copiedRates.putAll(rates);
        rates = Collections.unmodifiableMap(copiedRates);
        rates.values().forEach(rate -> {
            if (rate.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("rate must not be negative");
            }
        });
    }

    public static PricingSnapshot from(PricingPlan plan, String catalogVersion, Instant checkedAt) {
        return new PricingSnapshot(
                plan.modelId(),
                plan.pricingPolicyId(),
                catalogVersion,
                checkedAt,
                plan.rates(),
                plan.currency()
        );
    }
}
