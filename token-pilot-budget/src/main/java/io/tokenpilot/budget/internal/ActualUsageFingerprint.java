package io.tokenpilot.budget.internal;

import io.tokenpilot.budget.ActualUsageCommand;
import io.tokenpilot.budget.ReservationActualTokens;
import io.tokenpilot.budget.ReservationId;

/** 중복 actual callback을 민감하거나 무제한인 metadata 없이 식별합니다. */
record ActualUsageFingerprint(
        String requestId,
        String attemptId,
        ReservationId reservationId,
        ReservationActualTokens actualTokens,
        String responseModelId
) {

    static ActualUsageFingerprint from(
            ActualUsageCommand command,
            ReservationActualTokens actualTokens
    ) {
        return new ActualUsageFingerprint(
                command.requestId(),
                command.attemptId(),
                command.reservationId(),
                actualTokens,
                command.responseModelId()
        );
    }
}
