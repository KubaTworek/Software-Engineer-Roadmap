package pl.jakubtworek.backend_engineering.stage_1.block_c.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.MapPropertySource;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ValidatedExternalApiPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfig.class);

    @Test
    void shouldBindConfigurationToDomainTypes() {
        contextRunner
                .withPropertyValues(
                        "app.external-api.base-url=https://payments.example.com",
                        "app.external-api.api-key=test-secret",
                        "app.external-api.timeout=750ms"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    ValidatedExternalApiProperties properties =
                            context.getBean(ValidatedExternalApiProperties.class);
                    assertThat(properties.baseUrl()).isEqualTo(URI.create("https://payments.example.com"));
                    assertThat(properties.timeout()).isEqualTo(Duration.ofMillis(750));
                });
    }

    @Test
    void shouldFailFastForInvalidConfiguration() {
        contextRunner
                .withPropertyValues(
                        "app.external-api.base-url=ftp://files.example.com",
                        "app.external-api.timeout=50ms"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasRootCauseMessage(
                            "baseUrl must use http or https"
                    );
                });
    }

    @Test
    void shouldUseTheFirstPropertySourceAsTheEffectiveValue() {
        contextRunner
                .withInitializer(context -> {
                    context.getEnvironment().getPropertySources().addLast(new MapPropertySource(
                            "config-data",
                            Map.of(
                                    "app.external-api.base-url", "https://file.example.com",
                                    "app.external-api.timeout", "5s"
                            )
                    ));
                    context.getEnvironment().getPropertySources().addFirst(new MapPropertySource(
                            "command-line-demo",
                            Map.of("app.external-api.base-url", "https://cli.example.com")
                    ));
                })
                .run(context -> assertThat(context.getBean(ValidatedExternalApiProperties.class).baseUrl())
                        .isEqualTo(URI.create("https://cli.example.com")));
    }
}
