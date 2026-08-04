package io.tokenpilot.core.exception;

import io.tokenpilot.core.domain.PricingResolution;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MissingPricingExceptionTest {

    @Test
    @DisplayName("MissingPricingException은 PricingResolution을 구조화된 값으로 보존한다")
    void preservesPricingResolution() {
        MissingPricingException exception = new MissingPricingException(PricingResolution.MISSING_PLAN);

        assertThat(exception).hasMessage("MISSING_PLAN");
        assertThat(exception.getResolution()).isEqualTo(PricingResolution.MISSING_PLAN);
    }
}
