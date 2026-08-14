package io.tokenpilot.core.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModelDefinitionTest {

    private static final TokenizationBasis BASIS = new TokenizationBasis("o200k_base");
    private static final URI SOURCE = URI.create("https://example.com/models");
    private static final Instant SOURCE_AS_OF = Instant.parse("2026-08-14T00:00:00Z");

    @Test
    @DisplayName("model definition은 aliases와 source metadata를 방어적으로 보존한다")
    void copiesAliasesAndPreservesSourceMetadata() {
        Set<String> aliases = new HashSet<>(Set.of("gpt-alias"));
        ModelDefinition definition = new ModelDefinition(
                "gpt-versioned",
                aliases,
                "o200k_base",
                BASIS,
                128_000,
                "default",
                "catalog-v1",
                SOURCE,
                SOURCE_AS_OF
        );

        aliases.add("other-alias");

        assertThat(definition.aliases()).containsExactly("gpt-alias");
        assertThat(definition.sourceUri()).isEqualTo(SOURCE);
        assertThat(definition.sourceAsOf()).isEqualTo(SOURCE_AS_OF);
        assertThatThrownBy(() -> definition.aliases().add("new-alias"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("canonical id는 alias에 다시 등록할 수 없다")
    void rejectsCanonicalIdAsAlias() {
        assertThatThrownBy(() -> definition("gpt", Set.of("gpt")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    @DisplayName("canonical id와 encoding은 blank일 수 없다")
    void rejectsBlankIdentity(String value) {
        assertThatThrownBy(() -> new ModelDefinition(
                value,
                Set.of(),
                "o200k_base",
                BASIS,
                128_000,
                "default",
                "catalog-v1",
                SOURCE,
                SOURCE_AS_OF
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("context window와 source URI를 검증한다")
    void rejectsInvalidContextAndSource() {
        assertThatThrownBy(() -> new ModelDefinition(
                "gpt",
                Set.of(),
                "o200k_base",
                BASIS,
                0,
                "default",
                "catalog-v1",
                SOURCE,
                SOURCE_AS_OF
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ModelDefinition(
                "gpt",
                Set.of(),
                "o200k_base",
                BASIS,
                128_000,
                "default",
                "catalog-v1",
                URI.create("models"),
                SOURCE_AS_OF
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private static ModelDefinition definition(String canonicalId, Set<String> aliases) {
        return new ModelDefinition(
                canonicalId,
                aliases,
                "o200k_base",
                BASIS,
                128_000,
                "default",
                "catalog-v1",
                SOURCE,
                SOURCE_AS_OF
        );
    }
}
