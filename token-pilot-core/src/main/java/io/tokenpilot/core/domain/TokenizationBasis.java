package io.tokenpilot.core.domain;

public record TokenizationBasis(String id) {

    public TokenizationBasis {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
    }
}
