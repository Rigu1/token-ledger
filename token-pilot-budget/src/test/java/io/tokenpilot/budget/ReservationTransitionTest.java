package io.tokenpilot.budget;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.tokenpilot.budget.AccountingTransitionStatus.APPLIED;
import static io.tokenpilot.budget.AccountingTransitionStatus.NOT_ALLOWED;
import static io.tokenpilot.budget.ReservationState.IN_FLIGHT;
import static io.tokenpilot.budget.ReservationState.RESERVED;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReservationTransitionTest {

    @Test
    @DisplayName("적용된 전이는 예약 상태를 변경해야 한다")
    void rejectsAppliedTransitionWithoutStateChange() {
        assertThatThrownBy(() -> new ReservationTransition(RESERVED, RESERVED, APPLIED))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("적용되지 않은 전이는 예약 상태를 유지해야 한다")
    void rejectsUnappliedTransitionWithStateChange() {
        assertThatThrownBy(() -> new ReservationTransition(RESERVED, IN_FLIGHT, NOT_ALLOWED))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
