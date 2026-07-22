package io.tokenpilot.budget;

import java.time.Clock;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Objects;

/**
 * 설정된 시간대 기준의 월별 예산 기간입니다.
 *
 * @param value 예산이 적용되는 연월
 */
public record BudgetWindow(YearMonth value) {

  public BudgetWindow {
    Objects.requireNonNull(value, "value must not be null");
  }

  public static BudgetWindow resolve(Clock clock, ZoneId zoneId) {
    Objects.requireNonNull(clock, "clock must not be null");
    Objects.requireNonNull(zoneId, "zoneId must not be null");
    return new BudgetWindow(YearMonth.now(clock.withZone(zoneId)));
  }

  public static BudgetWindow parse(String value) {
    return new BudgetWindow(YearMonth.parse(value));
  }

  @Override
  public String toString() {
    return value.toString();
  }
}
