package io.tokenpilot.core.internal;

import io.tokenpilot.core.TokenEstimator;
import io.tokenpilot.core.domain.TokenCountAccuracy;
import io.tokenpilot.core.domain.TokenCountResult;
import io.tokenpilot.core.domain.TokenCountScope;
import io.tokenpilot.core.domain.TokenEstimatorDescriptor;
import io.tokenpilot.core.domain.TokenizationBasis;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class HeuristicTokenEstimator implements TokenEstimator {

    private static final int BYTES_PER_ESTIMATED_TOKEN = 4;
    private static final String ESTIMATOR_ID = "tokenpilot-utf8-byte-heuristic";
    private static final String ESTIMATOR_VERSION = "1";
    private static final String TOKENIZATION_BASIS_ID = "BYTE_LEVEL_BPE_UTF8";

    private static final TokenEstimatorDescriptor ESTIMATOR_DESCRIPTOR =
            new TokenEstimatorDescriptor(
                    ESTIMATOR_ID,
                    ESTIMATOR_VERSION
            );

    private static final TokenizationBasis TOKENIZATION_BASIS =
            new TokenizationBasis(TOKENIZATION_BASIS_ID);

    @Override
    public TokenCountResult estimate(String text) {
        Objects.requireNonNull(text, "text must not be null");

        long utf8Bytes = text.getBytes(StandardCharsets.UTF_8).length;
        long estimatedTokens = Math.ceilDiv(utf8Bytes, BYTES_PER_ESTIMATED_TOKEN);

        return TokenCountResult.counted(
                estimatedTokens,
                utf8Bytes,
                TokenCountAccuracy.HEURISTIC,
                TokenCountScope.TEXT_ONLY,
                ESTIMATOR_DESCRIPTOR,
                TOKENIZATION_BASIS
        );
    }


}
