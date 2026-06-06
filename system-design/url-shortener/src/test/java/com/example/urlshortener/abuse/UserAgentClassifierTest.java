package com.example.urlshortener.abuse;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UserAgentClassifierTest {
    private final UserAgentClassifier classifier = new UserAgentClassifier();

    @Test
    void detectsMobileDevice() {
        assertThat(classifier.deviceType("Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) Mobile/15E148"))
            .isEqualTo("mobile");
    }

    @Test
    void detectsBotDevice() {
        assertThat(classifier.deviceType("Googlebot/2.1 (+http://www.google.com/bot.html)"))
            .isEqualTo("bot");
    }

    @Test
    void detectsBrowser() {
        assertThat(classifier.browser("Mozilla/5.0 AppleWebKit/537.36 Chrome/125.0 Safari/537.36"))
            .isEqualTo("chrome");
    }
}
