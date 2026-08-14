package io.tokenpilot.core.domain;

import java.net.URI;
import java.time.Instant;
import java.util.Collections;
import java.util.Currency;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * context admission과 pricing policy 조회에 함께 사용하는 immutable model catalog 정의입니다.
 *
 * @param canonicalModelId versioned canonical model id
 * @param aliases canonical id로 해석할 exact aliases
 * @param encodingName 모델 encoding asset 이름
 * @param acceptedCompatibilityBasis heuristic/exact estimator가 사용할 수 있는 호환성 기준
 * @param maxContextTokens 모델 context window 크기
 * @param pricingPolicyId canonical pricing policy 식별자
 * @param catalogVersion model catalog 버전
 * @param sourceUri context/encoding metadata의 공식 출처
 * @param sourceAsOf source 확인 시점
 */
public record ModelDefinition(
        String canonicalModelId,
        Set<String> aliases,
        String encodingName,
        TokenizationBasis acceptedCompatibilityBasis,
        long maxContextTokens,
        String pricingPolicyId,
        Currency pricingCurrency,
        String catalogVersion,
        URI sourceUri,
        Instant sourceAsOf
) {

    public ModelDefinition {
        canonicalModelId = requireText(canonicalModelId, "canonicalModelId");
        aliases = copyAliases(aliases);
        if (aliases.contains(canonicalModelId)) {
            throw new IllegalArgumentException("aliases must not contain canonicalModelId");
        }
        encodingName = requireText(encodingName, "encodingName");
        acceptedCompatibilityBasis = Objects.requireNonNull(
                acceptedCompatibilityBasis,
                "acceptedCompatibilityBasis must not be null"
        );
        if (maxContextTokens <= 0) {
            throw new IllegalArgumentException("maxContextTokens must be greater than zero");
        }
        pricingPolicyId = requireText(pricingPolicyId, "pricingPolicyId");
        pricingCurrency = Objects.requireNonNull(pricingCurrency, "pricingCurrency must not be null");
        catalogVersion = requireText(catalogVersion, "catalogVersion");
        sourceUri = Objects.requireNonNull(sourceUri, "sourceUri must not be null");
        if (!sourceUri.isAbsolute()) {
            throw new IllegalArgumentException("sourceUri must be absolute");
        }
        sourceAsOf = Objects.requireNonNull(sourceAsOf, "sourceAsOf must not be null");
    }

    /**
     * 기존 모델 정의 생성 코드와의 호환성을 위해 USD를 기본 통화로 사용합니다.
     */
    public ModelDefinition(
            String canonicalModelId,
            Set<String> aliases,
            String encodingName,
            TokenizationBasis acceptedCompatibilityBasis,
            long maxContextTokens,
            String pricingPolicyId,
            String catalogVersion,
            URI sourceUri,
            Instant sourceAsOf
    ) {
        this(
                canonicalModelId,
                aliases,
                encodingName,
                acceptedCompatibilityBasis,
                maxContextTokens,
                pricingPolicyId,
                Currency.getInstance("USD"),
                catalogVersion,
                sourceUri,
                sourceAsOf
        );
    }

    private static Set<String> copyAliases(Set<String> aliases) {
        Objects.requireNonNull(aliases, "aliases must not be null");
        Set<String> copied = new LinkedHashSet<>();
        for (String alias : aliases) {
            copied.add(requireText(alias, "alias"));
        }
        return Collections.unmodifiableSet(copied);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
