package io.tokenpilot.budget;

/** 예약 정산 이벤트를 수신하는 framework-independent 계약입니다. */
@FunctionalInterface
public interface ReservationAccountingListener {

    void onCommitted(ReservationAccountingEvent event);
}
