package pl.jakubtworek.marketplace.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DomainLayerIndependenceTest {

    @Test
    void domainLayerShouldNotDependOnSpringJpaOrHttpFrameworks() throws IOException {
        Path sourceRoot = Path.of("src", "main", "java");
        List<Path> domainFiles;

        try (var stream = Files.walk(sourceRoot)) {
            domainFiles = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().replace('\\', '/').contains("/domain/"))
                    .filter(path -> path.toString().endsWith(".java"))
                    .toList();
        }

        assertThat(domainFiles).isNotEmpty();

        for (Path domainFile : domainFiles) {
            String source = Files.readString(domainFile);

            assertThat(source)
                    .as("Domain file should stay framework-free: %s", domainFile)
                    .doesNotContain("org.springframework")
                    .doesNotContain("jakarta.persistence")
                    .doesNotContain("javax.persistence")
                    .doesNotContain("@Entity")
                    .doesNotContain("@Service")
                    .doesNotContain("@Component")
                    .doesNotContain("@Repository")
                    .doesNotContain("@RestController");
        }
    }
}
