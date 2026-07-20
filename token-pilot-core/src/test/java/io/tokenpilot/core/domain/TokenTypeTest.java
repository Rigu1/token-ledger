package io.tokenpilot.core.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TokenTypeTest {

    @Test
    @DisplayName("completion 계열에는 일반·추론·cached output이 포함된다")
    void shouldClassifyAllCompletionTokenTypes() {
        assertThat(TokenType.COMPLETION.isCompletion()).isTrue();
        assertThat(TokenType.REASONING.isCompletion()).isTrue();
        assertThat(TokenType.CACHED_COMPLETION.isCompletion()).isTrue();
        assertThat(TokenType.PROMPT.isCompletion()).isFalse();
    }
}
