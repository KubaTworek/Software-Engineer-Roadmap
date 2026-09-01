package pl.jakubtworek.backend_engineering;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Protects the meaning of the two build lanes: the default build is fast, while
 * the infrastructure profile must execute every Docker-backed contract.
 */
class InfrastructureTestSeparationTest {

    @Test
    void everyTestcontainersSuiteIsExplicitlyClassified() throws IOException {
        Path testSources = moduleRoot().resolve("src/test/java");
        List<Path> containerSuites;

        try (var paths = Files.walk(testSources)) {
            containerSuites = paths
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.getFileName().toString()
                            .equals("InfrastructureTestSeparationTest.java"))
                    .filter(path -> read(path).contains(
                            "import org.testcontainers.junit.jupiter.Testcontainers;"))
                    .toList();
        }

        assertThat(containerSuites)
                .as("all known Docker-backed suites")
                .hasSize(5);

        for (Path suite : containerSuites) {
            String source = read(suite);
            assertThat(source)
                    .as("%s must be selected by the infrastructure profile", suite)
                    .contains("@Tag(\"infrastructure\")")
                    .doesNotContain("disabledWithoutDocker");
        }
    }

    @Test
    void mavenProfileAndCiRunTheSameInfrastructureLane() throws IOException {
        Path moduleRoot = moduleRoot();
        String pom = read(moduleRoot.resolve("pom.xml"));
        String workflow = read(moduleRoot.resolve("../.github/workflows/backend-engineering-ci.yml").normalize());

        assertThat(pom)
                .contains("<test.excluded-groups>infrastructure</test.excluded-groups>")
                .contains("<id>infrastructure-tests</id>")
                .contains("<test.groups>infrastructure</test.groups>");
        assertThat(workflow)
                .contains("infrastructure-tests:")
                .contains("-Pinfrastructure-tests verify");
    }

    private static Path moduleRoot() {
        Path workingDirectory = Path.of("").toAbsolutePath().normalize();
        if (Files.exists(workingDirectory.resolve("pom.xml"))) {
            return workingDirectory;
        }
        return workingDirectory.resolve("backend-engineering");
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read " + path, exception);
        }
    }
}
