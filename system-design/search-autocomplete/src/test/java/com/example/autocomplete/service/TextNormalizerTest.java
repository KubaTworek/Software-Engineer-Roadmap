package com.example.autocomplete.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TextNormalizerTest {

    private final TextNormalizer textNormalizer = new TextNormalizer();

    @Test
    void shouldNormalizeInput() {
        assertThat(textNormalizer.normalize("  iPhone-15 Pro!! "))
                .isEqualTo("iphone 15 pro");
    }

    @Test
    void shouldRemoveDiacritics() {
        assertThat(textNormalizer.normalize("Łódź"))
                .isEqualTo("lodz");
    }

    @Test
    void shouldReturnEmptyStringForNull() {
        assertThat(textNormalizer.normalize(null))
                .isEmpty();
    }
}
