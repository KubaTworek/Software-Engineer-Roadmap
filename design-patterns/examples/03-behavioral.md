# Wzorce behawioralne: algorytmy i przejścia

[Mapa przykładów](../LABORATORIA.md) · [Karty wzorców](../catalog/03-behavioral/README.md)

Ten dokument zawiera rozwinięty model i scenariusze jego poprawności w blokach Markdown. Karty katalogu
pozostają punktem wejścia do intuicji, konkretnych zastosowań, kosztu i kontrprzykładów. Modele mają zakres
lokalny i in-memory; trwałość oraz granice rozproszone wymagają osobnego kontraktu.

## Model

Model dokumentacyjny Java 21. Poniższy blok zawiera pełne typy, importy, komentarze i mechanizm przykładu. Nie
jest osobnym plikiem źródłowym ani uruchamianą aplikacją; ograniczenia i przebieg opisano w tym dokumencie.

<details>
<summary>Rozwiń pełny przykład: BehavioralPatterns</summary>

```java
package pl.jakubtworek.patterns.gof;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.LongUnaryOperator;

/**
 * Jedenaście GoF dotyczących algorytmów, komunikacji i przejść stanu.
 *
 * <p>Przykłady rozdzielają trzy decyzje: kto wybiera algorytm, kto przechowuje
 * postęp procesu i kto powiadamia odbiorców. Wszystkie są lokalne i jednowątkowe;
 * callback nie staje się trwałą wiadomością tylko dlatego, że nazwano go eventem.
 */
public final class BehavioralPatterns {
    private BehavioralPatterns() {
    }

    // CHAIN OF RESPONSIBILITY: pierwszy pasujący handler kończy poszukiwanie.
    public interface Handler {
        Optional<String> handle(String request);
    }

    public static String route(List<Handler> chain, String request) {
        for (Handler handler : chain) {
            var result = handler.handle(request);
            // Wariant „pierwszy obsługujący”: pozostałe handlery nie mogą już wykonać efektu.
            if (result.isPresent()) {
                return result.get();
            }
        }
        throw new IllegalArgumentException("unhandled: " + request);
    }

    // COMMAND: polecenie jest obiektem, można je zapisać w historii i odwrócić lokalny efekt.
    public static final class Counter {
        private int value;

        public int value() {
            return value;
        }
    }

    public interface Command {
        void execute();

        void undo();
    }

    public static final class Increment implements Command {
        private final Counter counter;
        private boolean applied;

        public Increment(Counter counter) {
            this.counter = counter;
        }

        public void execute() {
            if (applied) {
                throw new IllegalStateException("already applied");
            }
            // Flaga należy do konkretnej komendy, dzięki czemu undo wie, czy istnieje jej efekt.
            counter.value++;
            applied = true;
        }

        public void undo() {
            if (!applied) {
                throw new IllegalStateException("not applied");
            }
            counter.value--;
            applied = false;
        }
    }

    // INTERPRETER: drzewo składni reprezentuje mały język wyrażeń, bez parsera tekstowego.
    public sealed interface Expression permits Number, Add {
        long evaluate();
    }

    public record Number(long value) implements Expression {
        public long evaluate() {
            return value;
        }
    }

    public record Add(Expression left, Expression right) implements Expression {
        public long evaluate() {
            return Math.addExact(left.evaluate(), right.evaluate());
        }
    }

    // ITERATOR: ukrywamy reprezentację, zachowujemy kontrakt wyczerpania iteratora.
    public static final class Playlist implements Iterable<String> {
        private final List<String> songs;

        public Playlist(List<String> songs) {
            this.songs = List.copyOf(songs);
        }

        public Iterator<String> iterator() {
            return new Iterator<>() {

                private int index;

                public boolean hasNext() {
                    return index < songs.size();
                }

                public String next() {
                    // Nie polegamy na wyjątku z get(index): kontrakt Iterator wymaga tego konkretnego typu.
                    if (!hasNext()) {
                        throw new NoSuchElementException();
                    }
                    return songs.get(index++);
                }
            };
        }
    }

    // MEDIATOR: uczestnicy komunikują się z koordynatorem, bez sieci wzajemnych referencji.
    public static final class ChatRoom {
        private final List<Consumer<String>> participants = new ArrayList<>();

        public void join(Consumer<String> participant) {
            participants.add(participant);
        }

        public void send(String message) {
            List.copyOf(participants).forEach(p -> p.accept(message));
        }
    }

    // MEMENTO: snapshot jest niezmienny, odtwarzanie należy do właściciela stanu.
    public record Snapshot(String text) {
    }

    public static final class Editor {
        private String text = "";

        public void write(String text) {
            this.text = text;
        }

        public String text() {
            return text;
        }

        public Snapshot save() {
            return new Snapshot(text);
        }

        public void restore(Snapshot snapshot) {
            text = snapshot.text();
        }
    }

    // OBSERVER: lokalna synchroniczna subskrypcja; nie jest trwałym brokerem wiadomości.
    public static final class Events {
        private final List<Consumer<String>> listeners = new ArrayList<>();

        public AutoCloseable subscribe(Consumer<String> listener) {
            listeners.add(listener);
            // Uchwyt należy do subskrybenta: może zakończyć obserwację bez dostępu do listy producenta.
            return () -> listeners.remove(listener);
        }

        public void publish(String event) {
            // Snapshot dopuszcza wypisanie z callbacku. Zmiana listy dotyczy kolejnej publikacji.
            List.copyOf(listeners).forEach(l -> l.accept(event));
        }
    }

    // STATE: stan decyduje o dozwolonym przejściu. Klient nie ustawia go dowolnym setterem.
    public interface OrderState {
        OrderState pay();

        String name();
    }

    public record NewOrder() implements OrderState {
        public OrderState pay() {
            return new PaidOrder();
        }

        public String name() {
            return "NEW";
        }
    }

    public record PaidOrder() implements OrderState {
        public OrderState pay() {
            throw new IllegalStateException("already paid");
        }

        public String name() {
            return "PAID";
        }
    }

    public static final class Order {
        private OrderState state = new NewOrder();

        public void pay() {
            // Przypisanie nastąpi dopiero po udanym przejściu. Wyjątek zachowuje dotychczasowy stan.
            state = state.pay();
        }

        public String state() {
            return state.name();
        }
    }

    // STRATEGY: politykę podaje klient; zmiana algorytmu nie oznacza zmiany stanu procesu.
    public static long price(long cents, LongUnaryOperator discount) {
        if (cents < 0) {
            throw new IllegalArgumentException("negative price");
        }
        long result = discount.applyAsLong(cents);
        // Wymienność strategii nie zwalnia klienta z obrony kontraktu: rabat nie tworzy ujemnej ceny.
        if (result < 0 || result > cents) {
            throw new IllegalArgumentException("invalid discount");
        }
        return result;
    }

    // TEMPLATE METHOD: szkielet algorytmu jest final, podklasa dostarcza pojedynczy krok.
    public abstract static class Importer {
        public final List<String> run(String input) {
            // final chroni wspólną kolejność walidacja → normalizacja → parse → snapshot wyniku.
            if (input.isBlank()) {
                throw new IllegalArgumentException("empty input");
            }
            return List.copyOf(parse(input.strip()));
        }

        protected abstract List<String> parse(String input);
    }

    public static final class CsvImporter extends Importer {
        protected List<String> parse(String input) {
            return List.of(input.split(","));
        }
    }

    // VISITOR: nowa operacja nie wymaga zmiany każdej figury, nowa figura zmienia visitorów.
    public sealed interface Shape permits Circle, Rectangle {
        <T> T accept(Visitor<T> visitor);
    }

    public interface Visitor<T> {
        T visit(Circle circle);

        T visit(Rectangle rectangle);
    }

    public record Circle(double radius) implements Shape {
        // Tu this ma typ Circle. To drugi krok dyspozycji potrzebny do wybrania visit(Circle).
        public <T> T accept(Visitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public record Rectangle(double width, double height) implements Shape {
        public <T> T accept(Visitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    public static final class Area implements Visitor<Double> {
        public Double visit(Circle circle) {
            return Math.PI * circle.radius() * circle.radius();
        }

        public Double visit(Rectangle rectangle) {
            return rectangle.width() * rectangle.height();
        }
    }
}
```

</details>

## Scenariusze poprawności

Scenariusze poprawności w notacji JUnit. Pokazują dane, działanie i oczekiwany stan. Są materiałem
dokumentacyjnym; moduł nie ma zależności JUnit ani automatycznego uruchamiania tych asercji.

<details>
<summary>Rozwiń pełny przykład: BehavioralPatternsTest</summary>

```java
package pl.jakubtworek.patterns;

import static org.junit.jupiter.api.Assertions.*;
import static pl.jakubtworek.patterns.gof.BehavioralPatterns.*;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import pl.jakubtworek.patterns.gof.BehavioralPatterns.Number;

class BehavioralPatternsTest {
    @Test
    void chainStopsAtFirstHandlerAndReportsUnhandledInput() {
        Handler known = request -> request.equals("known") ? Optional.of("ok") : Optional.empty();
        Handler never = request -> fail("must stop after first match");
        assertEquals("ok", route(List.of(known, never), "known"));
        assertThrows(IllegalArgumentException.class, () -> route(List.of(known), "other"));
    }

    @Test
    void commandCanUndoOnlyAnAppliedEffect() {
        var counter = new Counter();
        var command = new Increment(counter);
        assertThrows(IllegalStateException.class, command::undo);
        command.execute();
        assertEquals(1, counter.value());
        assertThrows(IllegalStateException.class, command::execute);
        command.undo();
        assertEquals(0, counter.value());
    }

    @Test
    void interpreterEvaluatesNestedSyntaxTreeAndDetectsOverflow() {
        Expression expression = new Add(new Number(1), new Add(new Number(2), new Number(3)));
        assertEquals(6, expression.evaluate());
        assertThrows(ArithmeticException.class, () -> new Add(new Number(Long.MAX_VALUE), new Number(1)).evaluate());
    }

    @Test
    void iteratorHasIndependentPositionAndEnforcesExhaustion() {
        var playlist = new Playlist(List.of("a", "b"));
        var first = playlist.iterator();
        var second = playlist.iterator();
        assertEquals("a", first.next());
        assertEquals("a", second.next());
        assertEquals("b", first.next());
        assertFalse(first.hasNext());
        assertThrows(NoSuchElementException.class, first::next);
    }

    @Test
    void mediatorCoordinatesParticipants() {
        var room = new ChatRoom();
        var a = new ArrayList<String>();
        var b = new ArrayList<String>();
        room.join(a::add);
        room.join(b::add);
        room.send("hello");
        assertEquals(List.of("hello"), a);
        assertEquals(a, b);
    }

    @Test
    void mementoRestoresEarlierState() {
        var editor = new Editor();
        editor.write("before");
        var snapshot = editor.save();
        editor.write("after");
        editor.restore(snapshot);
        assertEquals("before", editor.text());
    }

    @Test
    void observerCanUnsubscribeAndDoesNotReceiveLaterEvents() throws Exception {
        var events = new Events();
        var received = new ArrayList<String>();
        var subscription = events.subscribe(received::add);
        events.publish("one");
        subscription.close();
        events.publish("two");
        assertEquals(List.of("one"), received);
    }

    @Test
    void stateRejectsForbiddenTransitionWithoutChangingState() {
        var order = new Order();
        assertEquals("NEW", order.state());
        order.pay();
        assertThrows(IllegalStateException.class, order::pay);
        assertEquals("PAID", order.state());
    }

    @Test
    void strategyCanBeReplacedAndCannotCreateNegativePrice() {
        assertEquals(900, price(1000, cents -> cents * 9 / 10));
        assertEquals(1000, price(1000, cents -> cents));
        assertThrows(IllegalArgumentException.class, () -> price(1000, cents -> -1));
    }

    @Test
    void templateMethodKeepsCommonValidation() {
        assertEquals(List.of("a", "b"), new CsvImporter().run(" a,b "));
        assertThrows(IllegalArgumentException.class, () -> new CsvImporter().run(" "));
    }

    @Test
    void visitorSelectsOperationForConcreteElement() {
        assertEquals(12.0, new Rectangle(3, 4).accept(new Area()), 0.0001);
        assertEquals(Math.PI, new Circle(1).accept(new Area()), 0.0001);
    }
}
```

</details>
