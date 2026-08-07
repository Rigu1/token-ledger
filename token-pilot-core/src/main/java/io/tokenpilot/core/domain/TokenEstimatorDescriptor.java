package io.tokenpilot.core.domain;

public record TokenEstimatorDescriptor(
        String estimatorId,
        String estimatorVersion
) {

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
