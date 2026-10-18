package pl.jakubtworek.backend_engineering.documentation;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class EditorialDocumentationTest {

    private static final Set<String> SCOPES = Set.of(
            "`fundament`",
            "`praktyka-produkcyjna`",
            "`temat-zaawansowany`");

    @Test
    void everyLaboratoryReadmeStartsWithATitleAndMaterialCard() throws IOException {
        List<Path> readmes;
        try (Stream<Path> paths = Files.walk(Path.of("src"))) {
            readmes = paths
                    .filter(path -> path.getFileName().toString().equals("README.md"))
                    .toList();
        }

        assertThat(readmes).isNotEmpty();
        for (Path readme : readmes) {
            String content = Files.readString(readme);
            assertThat(content)
                    .as("editorial contract for %s", readme)
                    .startsWith("# ")
                    .contains("<!-- material-card:start -->")
                    .contains("**Uczy:**")
                    .contains("**Typowy błąd:**")
                    .contains("**Najkrótsza weryfikacja:**")
                    .contains("**Katalog uruchomienia:**")
                    .contains("**Role klas:**")
                    .contains("**Granica:**")
                    .contains("<!-- material-card:end -->")
                    .doesNotContain("Uznanie pojedynczego wyniku dotyczącego");

            long topLevelHeadings = content.lines()
                    .filter(line -> line.startsWith("# "))
                    .count();
            assertThat(topLevelHeadings)
                    .as("one document title in %s", readme)
                    .isEqualTo(1);

            String scopeLine = content.lines()
                    .filter(line -> line.startsWith("> - **Zakres:**"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("missing scope in " + readme));
            assertThat(SCOPES)
                    .as("allowed scope in %s", readme)
                    .anyMatch(scopeLine::endsWith);
        }
    }

    @Test
    void referenceWorkshopHasExecutableInstructionsBeyondItsCard() throws IOException {
        Path workshop = Path.of("src/main/java/pl/jakubtworek/backend_engineering/stage_2/block_c/workshop");
        String readme = Files.readString(workshop.resolve("README.md"));
        assertThat(readme).contains("## Problem i niezmiennik", "## Uruchomienie",
                "## Przepływ i endpointy", "## Dowody i ograniczenia",
                " -f src/main/java/pl/jakubtworek/backend_engineering/stage_2/block_c/workshop/pom.xml",
                "ApplicationHealthStateTest", "StorageServicesTest", "DeliveryArtifactsTest");
        assertThat(workshop.resolve("pom.xml")).exists();
    }
}
