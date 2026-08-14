package io.tokenpilot.core;

import io.tokenpilot.core.domain.BudgetResult;
import io.tokenpilot.core.domain.TokenCountResult;

/**
 * provider 호출 전에 입력 token과 예약 출력량의 context admission을 판단합니다.
 */
public interface TokenBudget {

    /**
     * model context window 안에 요청이 들어갈 수 있는지 fail-closed로 확인합니다.
     *
     * @param modelId canonical model id 또는 exact alias
     * @param input 입력 token 계산 결과
     * @param reservedOutputTokens 호출 전에 확보할 최대 출력 token 수
     * @return FITS, EXCEEDS 또는 INDETERMINATE를 구분한 결과
     * @throws IllegalArgumentException reservedOutputTokens가 음수인 경우
     */
    BudgetResult check(
            String modelId,
            TokenCountResult input,
            long reservedOutputTokens
    );

    /**
     * FITS가 아니면 provider 호출을 진행하지 않도록 예외를 던집니다.
     *
     * @return FITS 결과
     * @throws IllegalStateException admission 결과가 FITS가 아닌 경우
     */
    default BudgetResult requireFits(
            String modelId,
            TokenCountResult input,
            long reservedOutputTokens
    ) {
        BudgetResult result = check(modelId, input, reservedOutputTokens);
        if (!result.fits()) {
            throw new IllegalStateException("Token admission rejected: " + result.reason());
        }
        return result;
    }
}
