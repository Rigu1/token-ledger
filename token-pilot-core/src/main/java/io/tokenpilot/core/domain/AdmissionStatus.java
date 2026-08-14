package io.tokenpilot.core.domain;

/**
 * 입력 token과 예약 출력량의 context admission 상태입니다.
 */
public enum AdmissionStatus {
    /** 요청 전체가 안전한 상한 안에 있습니다. */
    FITS,
    /** 선언된 보수적 상한만으로 context window를 넘습니다. */
    EXCEEDS,
    /** 안전하게 허용 또는 초과를 확정할 정보가 부족합니다. */
    INDETERMINATE
}
