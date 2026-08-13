package io.tokenpilot.core.internal;

import io.tokenpilot.core.TokenEstimator;
import io.tokenpilot.core.domain.TokenCountAccuracy;
import io.tokenpilot.core.domain.TokenCountResult;
import io.tokenpilot.core.domain.TokenCountScope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.io.File;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HeuristicTokenEstimatorTest {

    private final TokenEstimator estimator = LedgerComponents.utf8ByteHeuristicTokenEstimator();

    @Test
    @DisplayName("빈 문자열은 estimate와 upper bound가 0인 결과로 계산한다")
    void estimatesEmptyTextWithZeroEstimateAndUpperBound() {
        TokenCountResult result = estimator.estimate("");

        assertThat(result).isNotNull();
        assertThat(result.isCounted()).isTrue();
        assertThat(result.isUnavailable()).isFalse();
        assertThat(result.tokens()).hasValue(0L);
        assertThat(result.safeUpperBoundTokens()).hasValue(0L);
        assertThat(result.isExact()).isFalse();
        assertThat(result.accuracy()).contains(TokenCountAccuracy.HEURISTIC);
        assertThat(result.scope()).isEqualTo(TokenCountScope.TEXT_ONLY);
    }

    @Test
    @DisplayName("4로 나누어떨어지는 ASCII byte 길이로 estimate를 계산한다")
    void estimatesAsciiTextWhenUtf8ByteLengthIsDivisibleByFour() {
        TokenCountResult result = estimator.estimate("four");

        assertThat(result.tokens()).hasValue(1L);
        assertThat(result.safeUpperBoundTokens()).hasValue(4L);
    }

    @Test
    @DisplayName("4보다 짧은 ASCII byte 길이의 estimate를 올림한다")
    void roundsUpAsciiTextShorterThanFourBytes() {
        TokenCountResult result = estimator.estimate("abc");

        assertThat(result.tokens()).hasValue(1L);
        assertThat(result.safeUpperBoundTokens()).hasValue(3L);
    }

    @Test
    @DisplayName("나머지가 있는 ASCII byte 길이의 estimate를 올림한다")
    void roundsUpAsciiTextWhenUtf8ByteLengthHasRemainder() {
        TokenCountResult result = estimator.estimate("hello");

        assertThat(result.tokens()).hasValue(2L);
        assertThat(result.safeUpperBoundTokens()).hasValue(5L);
    }

    @Test
    @DisplayName("한글 문자열을 UTF-8 byte 길이로 계산한다")
    void estimatesKoreanTextFromUtf8ByteLength() {
        TokenCountResult result = estimator.estimate("한글");

        assertThat(result.tokens()).hasValue(2L);
        assertThat(result.safeUpperBoundTokens()).hasValue(6L);
        assertHeuristicTextOnlyMetadata(result);
    }

    @Test
    @DisplayName("ASCII와 한글이 섞인 문자열을 UTF-8 byte 길이로 계산한다")
    void estimatesMixedAsciiAndKoreanTextFromUtf8ByteLength() {
        TokenCountResult result = estimator.estimate("A한");

        assertThat(result.tokens()).hasValue(1L);
        assertThat(result.safeUpperBoundTokens()).hasValue(4L);
        assertHeuristicTextOnlyMetadata(result);
    }

    @Test
    @DisplayName("모든 결과에 고정된 estimator와 tokenization 기준을 포함한다")
    void includesFixedEstimatorAndTokenizationMetadata() {
        TokenCountResult result = estimator.estimate("");

        assertThat(result.estimatorDescriptor().estimatorId())
                .isEqualTo("tokenpilot-utf8-byte-heuristic");
        assertThat(result.estimatorDescriptor().estimatorVersion()).isEqualTo("1");
        assertThat(result.tokenizationBasis().id()).isEqualTo("BYTE_LEVEL_BPE_UTF8");
    }

    @Test
    @DisplayName("null 입력을 거부한다")
    void rejectsNullText() {
        assertThatThrownBy(() -> estimator.estimate(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("짝이 없는 high surrogate를 거부한다")
    void rejectsUnpairedHighSurrogate() {
        assertThatThrownBy(() -> estimator.estimate("\uD800"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("짝이 없는 low surrogate를 거부한다")
    void rejectsUnpairedLowSurrogate() {
        assertThatThrownBy(() -> estimator.estimate("\uDC00"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("올바른 surrogate pair를 UTF-8 byte 길이로 계산한다")
    void estimatesValidSurrogatePairFromUtf8ByteLength() {
        TokenCountResult result = estimator.estimate("\uD83D\uDE00");

        assertThat(result.tokens()).hasValue(1L);
        assertThat(result.safeUpperBoundTokens()).hasValue(4L);
        assertHeuristicTextOnlyMetadata(result);
    }

    @Test
    @DisplayName("precomposed 문자열을 원문의 UTF-8 byte 길이로 계산한다")
    void estimatesPrecomposedTextWithoutNormalization() {
        TokenCountResult result = estimator.estimate("\u00E9");

        assertThat(result.tokens()).hasValue(1L);
        assertThat(result.safeUpperBoundTokens()).hasValue(2L);
    }

    @Test
    @DisplayName("combining 문자열을 원문의 UTF-8 byte 길이로 계산한다")
    void estimatesCombiningTextWithoutNormalization() {
        TokenCountResult result = estimator.estimate("e\u0301");

        assertThat(result.tokens()).hasValue(1L);
        assertThat(result.safeUpperBoundTokens()).hasValue(3L);
    }

    @Test
    @DisplayName("canonical equivalent 문자열을 같은 byte 길이로 정규화하지 않는다")
    void preservesDifferentByteLengthsForCanonicalEquivalentText() {
        String precomposed = "\u00E9";
        String combining = "e\u0301";
        assertThat(Normalizer.normalize(combining, Normalizer.Form.NFC))
                .isEqualTo(precomposed);

        TokenCountResult precomposedResult = estimator.estimate(precomposed);
        TokenCountResult combiningResult = estimator.estimate(combining);

        assertThat(precomposedResult.safeUpperBoundTokens()).hasValue(2L);
        assertThat(combiningResult.safeUpperBoundTokens()).hasValue(3L);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unicodeCorpus")
    @DisplayName("고정 Unicode corpus를 UTF-16, code point, UTF-8 byte 기준으로 구분한다")
    void estimatesFixedUnicodeCorpusFromUtf8Bytes(
            String label,
            String text,
            int expectedCodeUnits,
            int expectedCodePoints,
            int expectedUtf8Bytes
    ) {
        TokenCountResult result = estimator.estimate(text);
        int codeUnits = text.length();
        int codePoints = text.codePointCount(0, text.length());
        int utf8Bytes = text.getBytes(StandardCharsets.UTF_8).length;
        String diagnostic = diagnostic(
                label,
                codeUnits,
                codePoints,
                utf8Bytes,
                result
        );

        assertThat(codeUnits).as(diagnostic).isEqualTo(expectedCodeUnits);
        assertThat(codePoints).as(diagnostic).isEqualTo(expectedCodePoints);
        assertThat(utf8Bytes).as(diagnostic).isEqualTo(expectedUtf8Bytes);
        assertThat(result.tokens())
                .as(diagnostic)
                .hasValue(Math.ceilDiv((long) expectedUtf8Bytes, 4L));
        assertThat(result.safeUpperBoundTokens())
                .as(diagnostic)
                .hasValue(expectedUtf8Bytes);
        assertHeuristicTextOnlyMetadata(result, diagnostic);
        assertThat(result.isExact()).as(diagnostic).isFalse();
        assertThat(result.tokens().orElseThrow())
                .as(diagnostic)
                .isLessThanOrEqualTo(result.safeUpperBoundTokens().orElseThrow());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("runtimeEnvironments")
    @DisplayName("locale와 JVM 기본 charset이 달라도 UTF-8 계산 계약을 유지한다")
    void usesExplicitUtf8IndependentlyOfLocaleAndJvmDefaultCharset(
            String label,
            String language,
            String country,
            String expectedLocale
    ) throws Exception {
        String javaExecutable = Path.of(
                System.getProperty("java.home"),
                "bin",
                System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java"
        ).toString();
        String classpath = String.join(
                File.pathSeparator,
                Path.of(RuntimeEnvironmentProbe.class.getProtectionDomain()
                        .getCodeSource().getLocation().toURI()).toString(),
                Path.of(LedgerComponents.class.getProtectionDomain()
                        .getCodeSource().getLocation().toURI()).toString()
        );
        ProcessBuilder processBuilder = new ProcessBuilder(
                javaExecutable,
                "-Dfile.encoding=COMPAT",
                "-Duser.language=" + language,
                "-Duser.country=" + country,
                "-cp",
                classpath,
                RuntimeEnvironmentProbe.class.getName()
        );
        processBuilder.environment().put("LC_ALL", "C");
        processBuilder.environment().put("LANG", "C");
        Process process = processBuilder.redirectErrorStream(true).start();

        try {
            assertThat(process.waitFor(10, TimeUnit.SECONDS)).as(label).isTrue();
            String output = new String(
                    process.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8
            );
            assertThat(process.exitValue()).as("%s: %s", label, output).isZero();
            assertThat(output)
                    .as(label)
                    .startsWith("charset=")
                    .doesNotStartWith("charset=UTF-8,")
                    .endsWith(
                            "locale=%s,tokens=4,safeUpperBound=15,"
                                    .formatted(expectedLocale)
                                    + "accuracy=HEURISTIC,scope=TEXT_ONLY,"
                                    + "estimatorId=tokenpilot-utf8-byte-heuristic,"
                                    + "estimatorVersion=1,basis=BYTE_LEVEL_BPE_UTF8"
                    );
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                if (!process.waitFor(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("child process did not terminate: " + label);
                }
            }
        }
    }

    private static Stream<Arguments> unicodeCorpus() {
        return Stream.of(
                Arguments.of("ASCII", "hello", 5, 5, 5),
                Arguments.of("Korean", "안녕하세요", 5, 5, 15),
                Arguments.of("mixed", "한글 English 123 !@#", 18, 18, 22),
                Arguments.of("precomposed", "\u00E9", 1, 1, 2),
                Arguments.of("combining", "e\u0301", 2, 2, 3),
                Arguments.of("regional indicators", "\uD83C\uDDF0\uD83C\uDDF7", 4, 2, 8),
                Arguments.of(
                        "family ZWJ",
                        "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67\u200D\uD83D\uDC66",
                        11,
                        7,
                        25
                )
        );
    }

    private static Stream<Arguments> runtimeEnvironments() {
        return Stream.of(
                Arguments.of("English locale", "en", "US", "en-US"),
                Arguments.of("Korean locale", "ko", "KR", "ko-KR"),
                Arguments.of("Turkish locale", "tr", "TR", "tr-TR")
        );
    }

    private static String diagnostic(
            String label,
            int codeUnits,
            int codePoints,
            int utf8Bytes,
            TokenCountResult result
    ) {
        return ("corpus=%s, codeUnits=%d, codePoints=%d, utf8Bytes=%d, "
                + "result={tokens=%s, safeUpperBound=%s, accuracy=%s, scope=%s, "
                + "estimator=%s, basis=%s}").formatted(
                label,
                codeUnits,
                codePoints,
                utf8Bytes,
                result.tokens(),
                result.safeUpperBoundTokens(),
                result.accuracy(),
                result.scope(),
                result.estimatorDescriptor(),
                result.tokenizationBasis()
        );
    }

    private static void assertHeuristicTextOnlyMetadata(TokenCountResult result) {
        assertHeuristicTextOnlyMetadata(result, "result=" + result);
    }

    private static void assertHeuristicTextOnlyMetadata(
            TokenCountResult result,
            String diagnostic
    ) {
        assertThat(result.accuracy()).as(diagnostic).contains(TokenCountAccuracy.HEURISTIC);
        assertThat(result.scope()).as(diagnostic).isEqualTo(TokenCountScope.TEXT_ONLY);
        assertThat(result.estimatorDescriptor().estimatorId())
                .as(diagnostic)
                .isEqualTo("tokenpilot-utf8-byte-heuristic");
        assertThat(result.estimatorDescriptor().estimatorVersion())
                .as(diagnostic)
                .isEqualTo("1");
        assertThat(result.tokenizationBasis().id())
                .as(diagnostic)
                .isEqualTo("BYTE_LEVEL_BPE_UTF8");
    }
}

final class RuntimeEnvironmentProbe {

    private RuntimeEnvironmentProbe() {
    }

    public static void main(String[] args) {
        TokenCountResult result = LedgerComponents.utf8ByteHeuristicTokenEstimator()
                .estimate("안녕하세요");
        System.out.print(("charset=%s,locale=%s,tokens=%d,safeUpperBound=%d,"
                + "accuracy=%s,scope=%s,estimatorId=%s,estimatorVersion=%s,basis=%s")
                .formatted(
                        Charset.defaultCharset().name(),
                        java.util.Locale.getDefault().toLanguageTag(),
                        result.tokens().orElseThrow(),
                        result.safeUpperBoundTokens().orElseThrow(),
                        result.accuracy().orElseThrow(),
                        result.scope(),
                        result.estimatorDescriptor().estimatorId(),
                        result.estimatorDescriptor().estimatorVersion(),
                        result.tokenizationBasis().id()
                ));
    }
}
