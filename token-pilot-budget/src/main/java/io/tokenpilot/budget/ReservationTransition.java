package io.tokenpilot.budget;

import java.util.Objects;

/**
 * 예약의 상태 전이 판단입니다.
 */
public record ReservationTransition(
        ReservationState previousState,
        ReservationState resultingState,
        AccountingTransitionStatus status
) {

    public ReservationTransition {
        Objects.requireNonNull(previousState, "previousState must not be null");
        Objects.requireNonNull(resultingState, "resultingState must not be null");
        Objects.requireNonNull(status, "status must not be null");

        boolean stateChanged = previousState != resultingState;
        if (status.isApplied() != stateChanged) {
            throw new IllegalArgumentException(
                    "APPLIED must change reservation state and all other statuses must preserve it"
            );
        }
    }

    public static ReservationTransition applied(
            ReservationState previousState,
            ReservationState resultingState
    ) {
        return new ReservationTransition(
                previousState,
                resultingState,
                AccountingTransitionStatus.APPLIED
        );
    }

    public static ReservationTransition unchanged(
            ReservationState state,
            AccountingTransitionStatus status
    ) {
        return new ReservationTransition(state, state, status);
    }
}
