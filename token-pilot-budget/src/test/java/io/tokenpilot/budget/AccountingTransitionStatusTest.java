package io.tokenpilot.budget;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class AccountingTransitionStatusTest {

    @Test
    @DisplayName("APPLIED만 새로운 회계 변경을 뜻한다")
    void onlyAppliedMeansANewAccountingChange() {
        assertThat(AccountingTransitionStatus.APPLIED.isApplied()).isTrue();
    }

    @Test
    @DisplayName("APPLIED 외의 결과는 회계 상태를 변경하지 않는다")
    void statusesOtherThanAppliedDoNotChangeAccountingState() {
        assertThat(Arrays.stream(AccountingTransitionStatus.values())
                .filter(status -> status != AccountingTransitionStatus.APPLIED))
                .allMatch(status -> !status.isApplied());
    }
}
