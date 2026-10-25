# Wzorce strukturalne: kontrakty i kompozycja

[Mapa przykładów](../LABORATORIA.md) · [Karty wzorców](../catalog/02-structural/README.md)

Ten dokument zawiera rozwinięty model i scenariusze jego poprawności w blokach Markdown. Karty katalogu
pozostają punktem wejścia do intuicji, konkretnych zastosowań, kosztu i kontrprzykładów. Modele mają zakres
lokalny i in-memory; trwałość oraz granice rozproszone wymagają osobnego kontraktu.

## Model

Model dokumentacyjny Java 21. Poniższy blok zawiera pełne typy, importy, komentarze i mechanizm przykładu. Nie
jest osobnym plikiem źródłowym ani uruchamianą aplikacją; ograniczenia i przebieg opisano w tym dokumencie.

<details>
<summary>Rozwiń pełny przykład: StructuralPatterns</summary>

```java
package pl.jakubtworek.patterns.gof;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Siedem sposobów organizowania współpracy obiektów.
 *
 * <p>Adapter, Decorator i Proxy wszystkie delegują, ale różnią się intencją:
 * tłumaczą kontrakt, rozszerzają zachowanie lub kontrolują dostęp. Test powinien
 * pokazać tę różnicę, zamiast sprawdzać jedynie istnienie obiektu opakowującego.
 */
public final class StructuralPatterns {
    private StructuralPatterns() {
    }

    // ADAPTER: port klienta operuje w groszach, stare API przyjmuje tekst w tej samej jednostce.
    public interface Payments {
        String pay(long cents);
    }

    public static final class LegacyBank {
        public String charge(String cents) {
            return "charged:" + cents;
        }
    }

    public record BankAdapter(LegacyBank bank) implements Payments {
        public String pay(long cents) {
            if (cents <= 0) {
                throw new IllegalArgumentException("positive amount required");
            }
            // Nie dzielimy przez 100: oba kontrakty operują w groszach, różni się reprezentacja.
            return bank.charge(Long.toString(cents));
        }
    }

    // BRIDGE: rodzaj powiadomienia i kanał są niezależnymi osiami zmiany.
    public interface Channel {
        String send(String text);
    }

    public abstract static class Notification {
        protected final Channel channel;

        protected Notification(Channel channel) {
            this.channel = channel;
        }

        public abstract String send(String text);
    }

    public static final class Alert extends Notification {
        public Alert(Channel channel) {
            super(channel);
        }

        public String send(String text) {
            return channel.send("ALERT " + text);
        }
    }

    public static final class Receipt extends Notification {
        public Receipt(Channel channel) {
            super(channel);
        }

        public String send(String text) {
            return channel.send("RECEIPT " + text);
        }
    }

    // COMPOSITE: klient sumuje liść i grupę przez ten sam kontrakt.
    public interface Item {
        long price();
    }

    public record Product(long price) implements Item {
    }

    public record Bundle(List<Item> children) implements Item {
        public Bundle {
            children = List.copyOf(children);
        }

        // Każde dziecko odpowiada za swój fragment drzewa. Pusta grupa ma naturalną sumę zero.
        public long price() {
            return children.stream().mapToLong(Item::price).sum();
        }
    }

    // DECORATOR: opakowanie zachowuje interfejs i składa zachowania w zadanej kolejności.
    public interface Text {
        String read();
    }

    public record PlainText(String read) implements Text {
    }

    public record Brackets(Text inner) implements Text {
        public String read() {
            return "[" + inner.read() + "]";
        }
    }

    public record Uppercase(Text inner) implements Text {
        public String read() {
            return inner.read().toUpperCase(java.util.Locale.ROOT);
        }
    }

    // FACADE: upraszcza lokalny podsystem; samo opakowanie nie daje transakcji między usługami.
    public interface Inventory {
        void reserve(String id);
    }

    public interface Shipping {
        String label(String id);
    }

    public record Checkout(Inventory inventory, Shipping shipping) {
        // Wyjątek shipping nie cofnie reserve. To granica fasady, a nie ukryta transakcja.
        public String buy(String id) {
            inventory.reserve(id);
            return shipping.label(id);
        }
    }

    // FLYWEIGHT: współdzielimy tylko niezmienny styl. Pozycja należy do wystąpienia znaku.
    public record GlyphStyle(String font, int size) {
    }

    public static final class Styles {
        private final Map<GlyphStyle, GlyphStyle> pool = new HashMap<>();

        public GlyphStyle get(String font, int size) {
            var key = new GlyphStyle(font, size);
            // Równość rekordu umożliwia znalezienie już istniejącego stylu, a nie tylko podobnej kopii.
            return pool.computeIfAbsent(key, ignored -> key);
        }
    }

    public record Glyph(char character, int x, GlyphStyle style) {
    }

    // PROXY: inicjalizacja dopiero przy pierwszym użyciu, kontrakt taki sam jak obiektu docelowego.
    public interface Image {
        String pixels();
    }

    public static final class LazyImage implements Image {
        private final Supplier<Image> loader;
        private Image target;

        public LazyImage(Supplier<Image> loader) {
            this.loader = loader;
        }

        public String pixels() {
            // Publikacja target nie jest synchronizowana: ten wariant ma jednego właściciela wątku.
            // Jeśli loader rzuci wyjątek, target pozostaje null i następna próba może spróbować ponownie.
            if (target == null) {
                target = loader.get();
            }
            return target.pixels();
        }
    }
}
```

</details>

## Scenariusze poprawności

Scenariusze poprawności w notacji JUnit. Pokazują dane, działanie i oczekiwany stan. Są materiałem
dokumentacyjnym; moduł nie ma zależności JUnit ani automatycznego uruchamiania tych asercji.

<details>
<summary>Rozwiń pełny przykład: StructuralPatternsTest</summary>

```java
package pl.jakubtworek.patterns;

import static org.junit.jupiter.api.Assertions.*;
import static pl.jakubtworek.patterns.gof.StructuralPatterns.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class StructuralPatternsTest {
    @Test
    void adapterPreservesUnitsAndRejectsInvalidAmount() {
        var payments = new BankAdapter(new LegacyBank());
        assertEquals("charged:1250", payments.pay(1250));
        assertThrows(IllegalArgumentException.class, () -> payments.pay(-1));
    }

    @Test
    void bridgeCombinesTwoIndependentDimensions() {
        Channel email = text -> "email:" + text;
        Channel sms = text -> "sms:" + text;
        assertEquals("email:ALERT outage", new Alert(email).send("outage"));
        assertEquals("sms:ALERT outage", new Alert(sms).send("outage"));
        assertEquals("sms:RECEIPT paid", new Receipt(sms).send("paid"));
    }

    @Test
    void compositeHandlesNestedAndEmptyGroups() {
        Item tree = new Bundle(List.of(new Product(100), new Bundle(List.of(new Product(200)))));
        assertEquals(300, tree.price());
        assertEquals(0, new Bundle(List.of()).price());
    }

    @Test
    void decoratorComposesWithoutChangingOriginal() {
        Text original = new PlainText("hello");
        assertEquals("[HELLO]", new Brackets(new Uppercase(original)).read());
        assertEquals("hello", original.read());
    }

    @Test
    void facadeOrdersSubsystemCallsAndStopsAfterFailure() {
        var calls = new ArrayList<String>();
        var checkout = new Checkout(id -> calls.add("reserve:" + id), id -> {
            calls.add("label:" + id);
            return "L1";
        });
        assertEquals("L1", checkout.buy("o1"));
        assertEquals(List.of("reserve:o1", "label:o1"), calls);
        var broken = new Checkout(id -> {
            throw new IllegalStateException();
        }, id -> fail("no shipping after failure"));
        assertThrows(IllegalStateException.class, () -> broken.buy("o2"));
    }

    @Test
    void flyweightSharesIntrinsicStateOnly() {
        var styles = new Styles();
        var a = new Glyph('A', 0, styles.get("mono", 12));
        var b = new Glyph('B', 10, styles.get("mono", 12));
        assertSame(a.style(), b.style());
        assertNotEquals(a.x(), b.x());
        assertNotSame(a.style(), styles.get("mono", 14));
    }

    @Test
    void proxyLoadsOnFirstUseOnly() {
        var loads = new AtomicInteger();
        var image = new LazyImage(() -> {
            loads.incrementAndGet();
            return () -> "pixels";
        });
        assertEquals(0, loads.get());
        assertEquals("pixels", image.pixels());
        assertEquals("pixels", image.pixels());
        assertEquals(1, loads.get());
    }
}
```

</details>
