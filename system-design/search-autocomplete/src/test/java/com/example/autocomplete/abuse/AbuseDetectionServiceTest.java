package com.example.autocomplete.abuse;

import com.example.autocomplete.model.AutocompleteContext;
import org.junit.jupiter.api.Test;
import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;

class AbuseDetectionServiceTest {
    @Test
    void shouldAllowNormalRequest() {
        AbuseDetectionService service = new AbuseDetectionService(Clock.systemUTC());
        assertThat(service.isAllowed(new AutocompleteContext("u", "s", "en-US", "US", null, "ip", "ip", "127.0.0.1"))).isTrue();
    }
}
