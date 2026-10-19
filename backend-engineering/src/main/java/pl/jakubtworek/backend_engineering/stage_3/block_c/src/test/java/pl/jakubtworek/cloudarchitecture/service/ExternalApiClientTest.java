package pl.jakubtworek.cloudarchitecture.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import pl.jakubtworek.cloudarchitecture.service.ExternalApiClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ExternalApiClientTest {

    @Test
    void exposesTheConfiguredTimeout() {
        ExternalApiClient client = new ExternalApiClient(
                RestClient.builder(),
                "https://api.example.com",
                Duration.ofMillis(750)
        );

        assertThat(client.recommendedTimeout()).isEqualTo(Duration.ofMillis(750));
    }

    @Test
    void rejectsUnsafeTimeoutConfiguration() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ExternalApiClient(
                RestClient.builder(),
                "https://api.example.com",
                Duration.ZERO
        ));
    }
}
