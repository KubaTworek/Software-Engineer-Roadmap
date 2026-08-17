package com.example.autocomplete.privacy;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QueryPrivacyServiceTest {
    private final QueryPrivacyService service = new QueryPrivacyService();

    @Test
    void shouldRedactEmailPhoneAndLongNumbers() {
        assertThat(service.redactPii("mail john@example.com phone +48 123 456 789 card 123456789012"))
                .contains("[email]").contains("[phone]").contains("[number]");
    }

    @Test
    void shouldHashUserId() {
        assertThat(service.hashUserId("u1")).hasSize(16);
    }
}
