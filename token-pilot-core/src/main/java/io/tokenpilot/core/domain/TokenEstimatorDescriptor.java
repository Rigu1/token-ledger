package io.tokenpilot.core.domain;

/**
 * token 계산에 사용된 estimator 구현과 버전을 식별합니다.
 *
 * @param estimatorId 안정적인 estimator 구현 식별자
 * @param estimatorVersion 계산식이나 asset 변경을 구분하는 버전
 */
public record TokenEstimatorDescriptor(
        String estimatorId,
        String estimatorVersion
) {

    /**
     * estimator 식별자와 버전이 null 또는 blank가 아닌지 검증합니다.
     *
     * @throws IllegalArgumentException estimatorId 또는 estimatorVersion이 null이거나 blank인 경우
     */
    public TokenEstimatorDescriptor {
        estimatorId = requireText(estimatorId, "estimatorId");
        estimatorVersion = requireText(estimatorVersion, "estimatorVersion");
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
