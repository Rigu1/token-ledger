package io.tokenpilot.core.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TokenTypeTest {

    @Test
    @DisplayName("completion 계열에는 전체 출력과 reasoning breakdown이 포함된다")
    void shouldClassifyCompletionTokenTypes() {
        assertThat(TokenType.COMPLETION.isCompletion()).isTrue();
        assertThat(TokenType.REASONING.isCompletion()).isTrue();
        assertThat(TokenType.PROMPT.isCompletion()).isFalse();
    }

    @Test
    @DisplayName("prompt 계열에는 전체 입력과 cache read/create breakdown이 포함된다")
    void shouldClassifyPromptTokenTypes() {
        assertThat(TokenType.PROMPT.isPrompt()).isTrue();
        assertThat(TokenType.CACHE_READ_PROMPT.isPrompt()).isTrue();
        assertThat(TokenType.CACHE_CREATION_PROMPT.isPrompt()).isTrue();
        assertThat(TokenType.COMPLETION.isPrompt()).isFalse();
    }
}
