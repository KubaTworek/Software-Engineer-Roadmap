# Wzorce kreacyjne: wybór i tworzenie produktów

[Mapa przykładów](../LABORATORIA.md) · [Karty wzorców](../catalog/01-creational/README.md)

Ten dokument zawiera rozwinięty model i scenariusze jego poprawności w blokach Markdown. Karty katalogu
pozostają punktem wejścia do intuicji, konkretnych zastosowań, kosztu i kontrprzykładów. Modele mają zakres
lokalny i in-memory; trwałość oraz granice rozproszone wymagają osobnego kontraktu.

## Model

Model dokumentacyjny Java 21. Poniższy blok zawiera pełne typy, importy, komentarze i mechanizm przykładu. Nie
jest osobnym plikiem źródłowym ani uruchamianą aplikacją; ograniczenia i przebieg opisano w tym dokumencie.

<details>
<summary>Rozwiń pełny przykład: CreationalPatterns</summary>

```java
package pl.jakubtworek.patterns.gof;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Pięć niezależnych przykładów kreacyjnych GoF.
 *
 * <p>Czytaj każdy od kontraktu produktu do klienta: fabryka odpowiada na pytanie
 * „co utworzyć”, builder na „jak złożyć poprawny produkt”, a prototype na „co skopiować”.
 * Wspólny plik służy nawigacji; przykłady nie zależą od siebie i nie tworzą frameworka.
 */
public final class CreationalPatterns {
    private CreationalPatterns() {
    }

    // FACTORY METHOD: podklasa wybiera produkt; algorytm dostarczenia pozostaje wspólny.
    public interface Transport {
        String deliver();
    }

    public abstract static class Logistics {
        protected abstract Transport createTransport();

        // Klient nie wybiera produktu przez instanceof. Wariant zna wyłącznie kreator.
        public String ship() {
            return createTransport().deliver();
        }
    }

    public static final class RoadLogistics extends Logistics {
        @Override
        protected Transport createTransport() {
            return () -> "truck";
        }
    }

    public static final class SeaLogistics extends Logistics {
        @Override
        protected Transport createTransport() {
            return () -> "ship";
        }
    }

    // ABSTRACT FACTORY: rodzina produktów ma być spójna bez sprawdzania typu po stronie klienta.
    public interface Button {
        String render();
    }

    public interface Checkbox {
        String render();
    }

    public interface Widgets {
        Button button();

        Checkbox checkbox();
    }

    public static final class DarkWidgets implements Widgets {
        public Button button() {
            return () -> "dark-button";
        }

        public Checkbox checkbox() {
            return () -> "dark-checkbox";
        }
    }

    public static final class LightWidgets implements Widgets {
        public Button button() {
            return () -> "light-button";
        }

        public Checkbox checkbox() {
            return () -> "light-checkbox";
        }
    }

    public static List<String> screen(Widgets widgets) {
        // Podajemy jedną fabrykę dla całego ekranu, a nie osobno wybrane produkty różnych rodzin.
        return List.of(widgets.button().render(), widgets.checkbox().render());
    }

    // BUILDER: walidacja następuje przy budowie, produkt nie zachowuje mutowalnej listy buildera.
    public record Request(String path, List<String> headers) {
        public Request {
            if (path == null || !path.startsWith("/")) {
                throw new IllegalArgumentException("absolute path required");
            }
            // Kopia odcina alias do listy buildera. String jest niezmienny, więc tu kopia płytka wystarcza.
            headers = List.copyOf(headers);
        }
    }

    public static final class RequestBuilder {
        private String path = "/";
        private final List<String> headers = new ArrayList<>();

        public RequestBuilder path(String value) {
            path = value;
            return this;
        }

        public RequestBuilder header(String value) {
            headers.add(Objects.requireNonNull(value));
            return this;
        }

        public Request build() {
            // Waliduje produkt, dzięki czemu również wywołanie jego konstruktora zachowuje kontrakt.
            return new Request(path, headers);
        }
    }

    // PROTOTYPE: kopia kolekcji jest konieczna; samo skopiowanie referencji połączyłoby oba obiekty.
    public static final class Document {
        private final List<String> paragraphs;

        public Document(List<String> paragraphs) {
            this.paragraphs = new ArrayList<>(paragraphs);
        }

        public Document copy() {
            // Konstruktor kopiuje listę. Samo przypisanie paragraphs połączyłoby historie obu dokumentów.
            return new Document(paragraphs);
        }

        public void append(String paragraph) {
            paragraphs.add(paragraph);
        }

        public List<String> paragraphs() {
            return List.copyOf(paragraphs);
        }
    }

    // SINGLETON: enum zapewnia instancję w obrębie classloadera; nie tworzy blokady między procesami.
    public enum Configuration {
        INSTANCE;

        // Brak setterów jest tu celowy: jedna instancja nie oznacza bezpiecznej mutacji wielu wątków.
        public String applicationName() {
            return "pattern-lab";
        }
    }
}
```

</details>

## Scenariusze poprawności

Scenariusze poprawności w notacji JUnit. Pokazują dane, działanie i oczekiwany stan. Są materiałem
dokumentacyjnym; moduł nie ma zależności JUnit ani automatycznego uruchamiania tych asercji.

<details>
<summary>Rozwiń pełny przykład: CreationalPatternsTest</summary>

```java
package pl.jakubtworek.patterns;

import static org.junit.jupiter.api.Assertions.*;
import static pl.jakubtworek.patterns.gof.CreationalPatterns.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class CreationalPatternsTest {
    @Test
    void factoryMethodSelectsProductWithoutChangingAlgorithm() {
        assertEquals("truck", new RoadLogistics().ship());
        assertEquals("ship", new SeaLogistics().ship());
    }

    @Test
    void abstractFactoryKeepsProductFamilyConsistent() {
        assertEquals(List.of("dark-button", "dark-checkbox"), screen(new DarkWidgets()));
        assertEquals(List.of("light-button", "light-checkbox"), screen(new LightWidgets()));
    }

    @Test
    void builderValidatesAndDoesNotLeakMutableState() {
        var builder = new RequestBuilder().path("/orders").header("Accept: json");
        var request = builder.build();
        builder.header("X-Trace: 1");
        assertEquals(List.of("Accept: json"), request.headers());
        assertThrows(UnsupportedOperationException.class, () -> request.headers().add("oops"));
        assertThrows(IllegalArgumentException.class, () -> new RequestBuilder().path("orders").build());
    }

    @Test
    void prototypeSeparatesMutableCollections() {
        var original = new Document(List.of("intro"));
        var copy = original.copy();
        copy.append("new");
        assertEquals(List.of("intro"), original.paragraphs());
        assertEquals(List.of("intro", "new"), copy.paragraphs());
    }

    @Test
    void singletonReturnsSameInstanceWithinClassloader() {
        assertSame(Configuration.INSTANCE, Configuration.valueOf("INSTANCE"));
    }
}
```

</details>
