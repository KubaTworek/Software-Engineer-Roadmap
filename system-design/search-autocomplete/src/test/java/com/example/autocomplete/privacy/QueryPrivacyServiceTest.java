package com.example.autocomplete.privacy;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QueryPrivacyServiceTest {
    private final QueryPrivacyService service = new QueryPrivacyService("test-privacy-key-123456789");

    @Test
    void shouldRedactEmailPhoneAndLongNumbers() {
        assertThat(service.redactPii("mail john@example.com phone +48 123 456 789 card 123456789012"))
                .contains("[email]").contains("[phone]").contains("[number]");
    }

    @Test
    void shouldHashUserId() {
        assertThat(service.hashUserId("u1")).hasSize(24);
        assertThat(service.hashUserId("u1")).isNotEqualTo(new QueryPrivacyService("different-test-key-1234").hashUserId("u1"));
    }
}
