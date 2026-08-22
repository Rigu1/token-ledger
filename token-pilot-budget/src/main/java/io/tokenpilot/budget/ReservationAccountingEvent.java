package io.tokenpilot.budget;

import java.util.Objects;

/** 새롭게 적용된 예약 정산을 전달하는 회계 이벤트입니다. */
public record ReservationAccountingEvent(
        ReservationReconciliation reconciliation
) {

    public ReservationAccountingEvent {
        Objects.requireNonNull(
                reconciliation,
                "reconciliation must not be null"
        );
    }
}
