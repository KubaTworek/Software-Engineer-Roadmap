package pl.jakubtworek.backend_engineering.stage_3.block_b;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CollectorConfigurationTest {

    @Test
    void tailSamplingKeepsErrorsSlowTracesAndSmallBaseline() throws IOException {
        String configuration = Files.readString(resolveConfiguration());

        assertThat(configuration)
                .contains("tail_sampling:")
                .contains("decision_wait: 10s")
                .contains("type: status_code")
                .contains("status_codes: [ERROR]")
                .contains("type: latency")
                .contains("threshold_ms: 1000")
                .contains("type: probabilistic")
                .contains("sampling_percentage: 5")
                .contains("processors: [memory_limiter, tail_sampling, batch]")
                .contains("${env:TEMPO_OTLP_ENDPOINT}")
                .doesNotContain("password:", "api_key:");
    }

    private static Path resolveConfiguration() {
        Path relativePath = Path.of(
                "src", "main", "java", "pl", "jakubtworek", "backend_engineering",
                "stage_3", "block_b", "collector", "otel-collector-tail-sampling.yaml");
        Path moduleRelative = relativePath.toAbsolutePath();
        if (Files.isRegularFile(moduleRelative)) {
            return moduleRelative;
        }
        Path repositoryRelative = Path.of("backend-engineering")
                .resolve(relativePath)
                .toAbsolutePath();
        if (Files.isRegularFile(repositoryRelative)) {
            return repositoryRelative;
        }
        throw new IllegalStateException("Cannot locate tail-sampling Collector configuration");
    }
}
