package io.tokenpilot.core.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenizationBasisTest {

    @ParameterizedTest
    @DisplayName("tokenization basis ID는 null이거나 blank일 수 없다")
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void rejectsNullOrBlankId(String id) {
        assertThatThrownBy(() -> new TokenizationBasis(id))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
