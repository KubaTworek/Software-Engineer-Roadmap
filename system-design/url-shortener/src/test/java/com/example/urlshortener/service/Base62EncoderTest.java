package com.example.urlshortener.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class Base62EncoderTest {
    private final Base62Encoder encoder = new Base62Encoder();

    @Test
    void encodesZero() { assertThat(encoder.encode(0)).isEqualTo("0"); }

    @Test
    void encodesBaseBoundaries() {
        assertThat(encoder.encode(61)).isEqualTo("Z");
        assertThat(encoder.encode(62)).isEqualTo("10");
        assertThat(encoder.encode(3843)).isEqualTo("ZZ");
    }

    @Test
    void rejectsNegativeValues() {
        assertThatThrownBy(() -> encoder.encode(-1)).isInstanceOf(IllegalArgumentException.class);
    }
}
