package io.tokenpilot.core.domain;

/**
 * token 계산 결과와 모델 encoding의 호환성을 판단할 때 사용하는 계산 기준입니다.
 *
 * @param id tokenization 기준을 식별하는 안정적인 값
 */
public record TokenizationBasis(String id) {

    /**
     * tokenization 기준 식별자가 null 또는 blank가 아닌지 검증합니다.
     *
     * @throws IllegalArgumentException id가 null이거나 blank인 경우
     */
    public TokenizationBasis {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
    }
}
