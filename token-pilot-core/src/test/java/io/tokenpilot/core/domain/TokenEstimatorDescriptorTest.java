package io.tokenpilot.core.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenEstimatorDescriptorTest {

    @ParameterizedTest
    @DisplayName("estimatorId는 null이거나 blank일 수 없다")
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void rejectsNullOrBlankEstimatorId(String estimatorId) {
        assertThatThrownBy(() -> new TokenEstimatorDescriptor(estimatorId, "1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @DisplayName("estimatorVersion은 null이거나 blank일 수 없다")
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void rejectsNullOrBlankEstimatorVersion(String estimatorVersion) {
        assertThatThrownBy(() -> new TokenEstimatorDescriptor("test-estimator", estimatorVersion))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
