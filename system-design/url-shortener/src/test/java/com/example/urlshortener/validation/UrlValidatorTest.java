package com.example.urlshortener.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.urlshortener.exception.InvalidUrlException;
import org.junit.jupiter.api.Test;

class UrlValidatorTest {
    private final UrlValidator validator = new UrlValidator();

    @Test
    void acceptsPublicHttpUrls() {
        assertThat(validator.validatePublicHttpUrl("https://example.com/path?q=1").toString()).isEqualTo("https://example.com/path?q=1");
    }

    @Test
    void rejectsUnsupportedSchemes() {
        assertThatThrownBy(() -> validator.validatePublicHttpUrl("javascript:alert(1)")).isInstanceOf(InvalidUrlException.class);
    }

    @Test
    void rejectsLocalhost() {
        assertThatThrownBy(() -> validator.validatePublicHttpUrl("http://localhost:8080")).isInstanceOf(InvalidUrlException.class);
    }

    @Test
    void rejectsPrivateIp() {
        assertThatThrownBy(() -> validator.validatePublicHttpUrl("http://127.0.0.1/admin")).isInstanceOf(InvalidUrlException.class);
    }

    @Test
    void rejectsUrlWithUserInfo() {
        assertThatThrownBy(() -> validator.validatePublicHttpUrl("https://user:pass@example.com")).isInstanceOf(InvalidUrlException.class);
    }
}
