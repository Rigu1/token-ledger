package io.tokenpilot.budget;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReservationStateTest {

    @Test
    @DisplayName("정산 대기 상태는 종료 상태가 아니다")
    void pendingReconciliationStatesAreNotClosed() {
        assertThat(ReservationState.RESERVED.isClosed()).isFalse();
        assertThat(ReservationState.IN_FLIGHT.isClosed()).isFalse();
        assertThat(ReservationState.RECONCILIATION_REQUIRED.isClosed()).isFalse();
    }

    @Test
    @DisplayName("확정, 해제, 상각 상태만 종료 상태다")
    void committedReleasedAndWrittenOffStatesAreClosed() {
        assertThat(ReservationState.COMMITTED.isClosed()).isTrue();
        assertThat(ReservationState.RELEASED.isClosed()).isTrue();
        assertThat(ReservationState.WRITTEN_OFF.isClosed()).isTrue();
    }
}
