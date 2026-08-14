package io.tokenpilot.core.internal;

import io.tokenpilot.core.domain.ModelDefinition;
import io.tokenpilot.core.domain.TokenizationBasis;

import java.net.URI;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Set;

final class DefaultModelCatalog {

    private static final Instant SOURCE_AS_OF = Instant.parse("2026-08-14T00:00:00Z");
    private static final TokenizationBasis DEFAULT_TOKENIZATION_BASIS =
            new TokenizationBasis("BYTE_LEVEL_BPE_UTF8");

    private DefaultModelCatalog() {
    }

    static List<ModelDefinition> definitions() {
        return List.of(
                new ModelDefinition(
                        "gpt-4o-mini-2024-07-18",
                        Set.of("gpt-4o-mini"),
                        "o200k_base",
                        DEFAULT_TOKENIZATION_BASIS,
                        128_000,
                        "default",
                        Currency.getInstance("USD"),
                        "openai-2026-08-14",
                        URI.create("https://platform.openai.com/docs/models/gpt-4o-mini"),
                        SOURCE_AS_OF
                ),
                new ModelDefinition(
                        "gpt-4o-2024-08-06",
                        Set.of("gpt-4o"),
                        "o200k_base",
                        DEFAULT_TOKENIZATION_BASIS,
                        128_000,
                        "default",
                        Currency.getInstance("USD"),
                        "openai-2026-08-14",
                        URI.create("https://platform.openai.com/docs/models/gpt-4o"),
                        SOURCE_AS_OF
                )
        );
    }
}
