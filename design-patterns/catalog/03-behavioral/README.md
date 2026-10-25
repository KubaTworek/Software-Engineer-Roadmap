# GoF — wzorce behawioralne

[Moduł](../../README.md) · [Indeks](../../INDEKS.md) · [Materiały](../../MATERIALY.md#gof)

Wzorce obiektowe. **11 kart.**

**Status przykładów:** moduł dokumentacyjny. Fragmenty Java, SQL i HTTP oraz pseudokod są objaśnieniami
mechanizmu; kompletne modele i scenariusze poprawności również znajdują się w Markdown. Nie ma osobnej
aplikacji, buildu Java ani testów uruchamianych przez Maven.

**Jak rozumieć tę kategorię:** Wzorce behawioralne porządkują decyzje i przepływ sterowania. Strategy wybiera
algorytm, State wiąże zachowanie ze stanem, Command reprezentuje zamiar wykonania, a Observer organizuje
reakcje. Pozostałe rozdzielają przechodzenie po danych, operacje na typach, koordynację lub historię zmian.
Pytaj, kto podejmuje decyzję, kto przechowuje postęp i kiedy kończy się obsługa, zamiast próbować rozpoznawać
wzorzec wyłącznie po nazwie klasy.

## Spis kart

- [Chain of Responsibility](#chain-of-responsibility)
- [Command](#command)
- [Interpreter](#interpreter)
- [Iterator](#iterator)
- [Mediator](#mediator)
- [Memento](#memento)
- [Observer](#observer)
- [State](#state)
- [Strategy](#strategy)
- [Template Method](#template-method)
- [Visitor](#visitor)

## Chain of Responsibility

**Intuicja:** Jedno żądanie może pasować do kilku rodzajów obsługi: problem techniczny do wsparcia
technicznego, faktura do księgowości. Zamiast centralnego warunku znającego wszystkie przypadki, Chain of
Responsibility układa obsługujących w kolejności. Każdy sam rozstrzyga, czy przejąć sprawę, czy pozwolić
spróbować następnemu.

**Problem i mechanizm:** Żądanie przechodzi przez uporządkowane handlery aż do obsługi albo końca łańcucha.

**Role i współpraca:** Handler określa wynik „obsłużyłem” lub „nie mój przypadek”. Route przechodzi po liście
i kończy na pierwszym wyniku. Lista jest częścią konfiguracji, a kolejność i zachowanie przy braku dopasowania
częścią kontraktu.

**Przykład:** route() sprawdza handlery kolejno; pierwszy niepusty wynik kończy obsługę, a brak dopasowania
daje błąd.

**Mechanizm w kodzie:** fragment modelu Java w Markdown. Typy poniżej pokazują współpracę wewnątrz przykładu;
wspólny kontekst znajduje się w [mapie przykładów](../../LABORATORIA.md).

```java
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
```

**Konkretny przypadek użycia:** Kierowanie zgłoszeń wsparcia

- **Wejście:** Zgłoszenie oznaczone billing przechodzi przez obsługę techniczną, księgowość i fallback.
- **Przebieg:** Każdy handler odmawia albo zwraca wynik; księgowość kończy łańcuch.
- **Oczekiwany wynik:** Zgłoszenie jest obsłużone raz i nie trafia do fallback po sukcesie.
- **Błąd lub granica:** Odwrócenie kolejności szerokiego fallback i księgowości może ukryć właściwy handler.

**Kiedy zastosować:** Gdy zbiór i kolejność obsługujących mają być składane bez zmiany centralnego algorytmu.
Dla dwóch stałych przypadków zwykły warunek może być czytelniejszy; nie każda seria walidacji jest tym
wariantem łańcucha.

**Koszt i pułapka:** Kolejność jest częścią zachowania. Pipeline wykonujący wszystkie etapy nie jest tym samym
wariantem łańcucha.

**Rozwinięcie i ślad wykonania:** Handler zwracający Optional.empty() oznacza „nie obsłużyłem”, a nie sukces z
pustą odpowiedzią. Pierwszy wynik kończy iterację, co test potwierdza handlerem, który po dopasowaniu nie może
zostać wywołany. W łańcuchu walidatorów wykonujących wszystkie kontrole potrzebny byłby inny kontrakt: lista
naruszeń lub jawne przekazanie next.

<!-- correctness-example:start -->
**Scenariusz poprawności:** szkic asercji w konwencji JUnit pokazuje oczekiwane zachowanie. Jest treścią
dokumentacji, bez automatycznego uruchamiania w module.

```java
@Test
void chainStopsAtFirstHandlerAndReportsUnhandledInput() {
    Handler known = request -> request.equals("known") ? Optional.of("ok") : Optional.empty();
    Handler never = request -> fail("must stop after first match");
    assertEquals("ok", route(List.of(known, never), "known"));
    assertThrows(IllegalArgumentException.class, () -> route(List.of(known), "other"));
}
```
<!-- correctness-example:end -->

**Ćwiczenie sprawdzające:** Udowodnij, że po dopasowaniu następny handler nie zostaje wywołany.

## Command

**Intuicja:** Przycisk UI, scheduler i kolejka mogą chcieć uruchomić tę samą operację, nie znając szczegółów
jej wykonania. Command pakuje zamiar i argumenty w obiekt. Dzięki temu można przekazać go innemu wykonawcy,
umieścić w historii albo wyznaczyć moment wykonania niezależnie od momentu utworzenia.

**Problem i mechanizm:** Operacja i jej argumenty stają się obiektem przekazywanym do wykonania, kolejki lub
historii.

**Role i współpraca:** Counter jest odbiorcą efektu, Increment komendą, klient miejscem konfiguracji. Execute
stosuje zmianę, undo odwraca jej lokalny skutek, a applied pilnuje poprawnej pary tych wywołań. Historia
mogłaby przechowywać interfejs Command.

**Przykład:** Increment przechowuje Counter; execute() zmienia licznik, undo() odwraca tylko faktycznie
zastosowaną zmianę.

**Mechanizm w kodzie:** fragment modelu Java w Markdown. Typy poniżej pokazują współpracę wewnątrz przykładu;
wspólny kontekst znajduje się w [mapie przykładów](../../LABORATORIA.md).

```java
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
```

**Konkretny przypadek użycia:** Historia zmian licznika edytora

- **Wejście:** Komenda Increment dotyczy licznika 0, później użytkownik wybiera undo.
- **Przebieg:** Execute zapisuje własny lokalny efekt, undo cofa tylko zastosowaną komendę.
- **Oczekiwany wynik:** Licznik przechodzi 0 → 1 → 0.
- **Błąd lub granica:** Undo przed execute i drugie execute bez dozwolonego przejścia są odrzucane.

**Kiedy zastosować:** Gdy wykonanie ma zostać odłożone, zapisane jako zamiar lub objęte lokalnym undo. Proste
wywołanie metody jest lepsze, jeśli nie potrzebujesz żadnej z tych dodatkowych własności.

**Koszt i pułapka:** Undo lokalnej operacji nie cofa nieodwracalnej wysyłki czy płatności. Serializacja wymaga
wersjonowania komend.

**Rozwinięcie i ślad wykonania:** Flaga applied chroni parę execute/undo konkretnej instancji polecenia. Dwa
Increment to dwa legalne efekty, lecz dwukrotne execute jednego obiektu bez undo jest błędem. Kolejka i
historia mogą przechowywać Command, ale stan applied nie zastępuje trwałego klucza idempotencji. Przy wysyłce
wiadomości undo może oznaczać nową kompensującą wiadomość, a nie cofnięcie czasu.

<!-- correctness-example:start -->
**Scenariusz poprawności:** szkic asercji w konwencji JUnit pokazuje oczekiwane zachowanie. Jest treścią
dokumentacji, bez automatycznego uruchamiania w module.

```java
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
```
<!-- correctness-example:end -->

**Ćwiczenie sprawdzające:** Sprawdź undo przed execute i ponowne execute bez undo.

## Interpreter

**Intuicja:** Jeżeli użytkownik definiuje małe reguły lub wyrażenia, ich znaczenie można przedstawić jako
drzewo. Interpreter wiąże typ węzła z regułą interpretacji. Węzły proste oddają wartość, złożone interpretują
dzieci i łączą ich wyniki, więc składnia oraz sposób obliczania odpowiadają sobie bez jednego ogromnego
switcha.

**Problem i mechanizm:** Reguły małego języka reprezentuje drzewo obiektów z operacją interpretacji.

**Role i współpraca:** Expression jest wspólnym kontraktem węzła, Number liściem, Add złożeniem dwóch wyrażeń.
Evaluate schodzi rekurencyjnie do liczb i składa wynik. Parser tekstu byłby osobnym etapem tworzącym to
drzewo.

**Przykład:** Add(Number(1), Add(Number(2), Number(3))) oblicza 6; drzewo budujemy jawnie bez parsera.

**Mechanizm w kodzie:** fragment modelu Java w Markdown. Typy poniżej pokazują współpracę wewnątrz przykładu;
wspólny kontekst znajduje się w [mapie przykładów](../../LABORATORIA.md).

```java
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
```

**Konkretny przypadek użycia:** Mały język reguł kosztu

- **Wejście:** Wyrażenie ma postać 100 + (20 + 5), zbudowaną z Number i Add.
- **Przebieg:** Każdy Add interpretuje dzieci i łączy wynik; parser tekstowy byłby osobnym krokiem.
- **Oczekiwany wynik:** Wartość wyrażenia wynosi 125.
- **Błąd lub granica:** Overflow i nadmierna głębokość wymagają kontroli; samo drzewo nie jest pełnym
  bezpiecznym parserem.

**Kiedy zastosować:** Dla małego języka z niewielką gramatyką i prostą interpretacją. Przy dużym języku,
optymalizacji i dobrym raportowaniu błędów warto rozważyć narzędzia parsujące oraz osobne etapy analizy.

**Koszt i pułapka:** Przy dużej gramatyce rosną hierarchia i koszt rekurencji; walidacja składni jest odrębnym
etapem.

**Rozwinięcie i ślad wykonania:** Każdy węzeł drzewa odpowiada jednej regule gramatyki: Number kończy
rekurencję, Add wywołuje obie gałęzie i łączy wyniki. Dzięki Math.addExact błąd zakresu nie zmienia się w
pozornie poprawną ujemną liczbę. Parser, limity głębokości i źródło wejścia są osobnymi odpowiedzialnościami;
samo drzewo nie chroni przed zbyt głębokim zagnieżdżeniem.

<!-- correctness-example:start -->
**Scenariusz poprawności:** szkic asercji w konwencji JUnit pokazuje oczekiwane zachowanie. Jest treścią
dokumentacji, bez automatycznego uruchamiania w module.

```java
@Test
void interpreterEvaluatesNestedSyntaxTreeAndDetectsOverflow() {
    Expression expression = new Add(new Number(1), new Add(new Number(2), new Number(3)));
    assertEquals(6, expression.evaluate());
    assertThrows(ArithmeticException.class, () -> new Add(new Number(Long.MAX_VALUE), new Number(1)).evaluate());
}
```
<!-- correctness-example:end -->

**Ćwiczenie sprawdzające:** Dodaj mnożenie i test overflow; oddziel parser tekstu od ewaluatora.

## Iterator

**Intuicja:** Klient chce przejść po elementach, ale nie powinien wiedzieć, czy kolekcja używa tablicy, listy
czy własnego indeksu. Iterator przenosi stan przechodzenia do osobnego obiektu. Kolekcja odpowiada za dane,
iterator za aktualną pozycję, a klient za to, co zrobi z kolejnym elementem.

**Problem i mechanizm:** Udostępnia sekwencyjny dostęp do elementów bez ujawniania reprezentacji kolekcji.

**Role i współpraca:** Playlist jest kolekcją udostępniającą iterator. Iterator trzyma własny index, hasNext
odpowiada o dostępności, next zwraca element i przesuwa pozycję. Dwa iteratory nie muszą wpływać na siebie
nawet dla tej samej kolekcji.

**Przykład:** Playlist daje dwa niezależne iteratory; każdy ma własną pozycję, a next() po końcu rzuca
NoSuchElementException.

**Mechanizm w kodzie:** fragment modelu Java w Markdown. Typy poniżej pokazują współpracę wewnątrz przykładu;
wspólny kontekst znajduje się w [mapie przykładów](../../LABORATORIA.md).

```java
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
```

**Konkretny przypadek użycia:** Dwóch czytelników playlisty

- **Wejście:** Playlista zawiera A, B, C, a dwaj klienci zaczynają przejście.
- **Przebieg:** Każdy wywołuje iterator i posiada własną pozycję.
- **Oczekiwany wynik:** Po przejściu pierwszego do B drugi nadal może zaczynać od A.
- **Błąd lub granica:** Next po końcu zgłasza NoSuchElementException; zmiany kolekcji wymagają osobnego
  kontraktu.

**Kiedy zastosować:** Gdy potrzebujesz jednolitego przechodzenia bez ujawniania reprezentacji. Dla operacji na
całej kolekcji Stream lub metoda domenowa może być prostsza; nadal musisz określić zachowanie przy modyfikacji
danych.

**Koszt i pułapka:** Mutowalna kolekcja wymaga polityki zmian podczas iteracji; fail-fast nie jest gwarancją
bezpieczeństwa wątkowego.

**Rozwinięcie i ślad wykonania:** Stan iteracji jest tworzony w iterator(), więc dwa iteratory nie współdzielą
index. Kolejność elementów i wyjątek po końcu należą do publicznego kontraktu; ukrycie listy nie zwalnia z ich
zachowania. Snapshot wejściowych piosenek oznacza, że późniejsza zmiana listy przekazanej do konstruktora nie
zmieni trasy już utworzonej playlisty.

<!-- correctness-example:start -->
**Scenariusz poprawności:** szkic asercji w konwencji JUnit pokazuje oczekiwane zachowanie. Jest treścią
dokumentacji, bez automatycznego uruchamiania w module.

```java
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
```
<!-- correctness-example:end -->

**Ćwiczenie sprawdzające:** Sprawdź pustą listę, dwa iteratory i wyczerpanie.

## Mediator

**Intuicja:** Gdy każdy element formularza zna wszystkie pozostałe, zmiana jednej reguły aktualizacji tworzy
sieć zależności. Mediator zbiera reguły współpracy w jednym miejscu. Uczestnicy informują koordynatora o
zdarzeniach, a on decyduje, kogo i jak powiadomić, dzięki czemu nie muszą posiadać referencji do całej grupy.

**Problem i mechanizm:** Koordynator zbiera reguły współpracy obiektów, ograniczając ich wzajemne zależności.

**Role i współpraca:** ChatRoom jest koordynatorem, uczestnicy odbiorcami wiadomości. Join rejestruje kontakt,
send wykonuje uzgodnione rozgłoszenie. W bogatszym przykładzie koordynator mógłby decydować o moderacji lub
adresatach, pozostawiając uczestnikom samo przetwarzanie.

**Przykład:** ChatRoom przyjmuje uczestników i rozsyła wiadomość bez referencji uczestnika do pozostałych.

**Mechanizm w kodzie:** fragment modelu Java w Markdown. Typy poniżej pokazują współpracę wewnątrz przykładu;
wspólny kontekst znajduje się w [mapie przykładów](../../LABORATORIA.md).

```java
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
```

**Konkretny przypadek użycia:** Formularz wyboru dostawy

- **Wejście:** Zmiana kraju ma odświeżyć listę przewoźników i dostępność odbioru osobistego.
- **Przebieg:** Element formularza zgłasza zmianę mediatorowi, który steruje pozostałymi widokami.
- **Oczekiwany wynik:** Pola nie posiadają wzajemnie wszystkich referencji i wspólnych reguł reakcji.
- **Błąd lub granica:** Mediator nie powinien przejąć reguł ceny i płatności należących do domeny.

**Kiedy zastosować:** Gdy reguły dotyczą relacji wielu obiektów i powtarza się komunikacja każdy-z-każdym.
Jeśli cały problem to prosty sygnał do niezależnych obserwatorów, Observer może wymagać mniej centralnej
logiki.

**Koszt i pułapka:** Koordynator może przejąć zbyt wiele reguł; synchroniczny callback może zatrzymać
wszystkich.

**Rozwinięcie i ślad wykonania:** ChatRoom zna uczestników, ale pojedynczy uczestnik zna tylko punkt wysłania.
Dodanie moderacji zmienia koordynator, a nie każdą parę połączeń. Jest to synchroniczne rozgłoszenie do
aktualnego snapshotu: dołączenie z callbacku nie powoduje dostawy wstecz do nowego uczestnika. Dla wolnego
odbiorcy trzeba osobno zdecydować o kolejce, limicie i izolacji błędów.

<!-- correctness-example:start -->
**Scenariusz poprawności:** szkic asercji w konwencji JUnit pokazuje oczekiwane zachowanie. Jest treścią
dokumentacji, bez automatycznego uruchamiania w module.

```java
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
```
<!-- correctness-example:end -->

**Ćwiczenie sprawdzające:** Dodaj regułę moderacji i ustal zachowanie, gdy uczestnik rzuci wyjątek.

## Memento

**Intuicja:** Żeby cofnąć edycję, potrzebujesz wcześniejszego stanu, ale zewnętrzna historia nie powinna znać
wszystkich prywatnych pól edytora. Memento oddaje tworzenie i odtwarzanie snapshotu właścicielowi stanu.
Historia przechowuje uchwyt wcześniejszego obrazu, zamiast sama ustawiać wnętrze obiektu pole po polu.

**Problem i mechanizm:** Właściciel stanu tworzy snapshot, który można zachować i później odtworzyć.

**Role i współpraca:** Editor jest właścicielem, Snapshot mementem, a lista historii mogłaby być opiekunem
snapshotów. Save zapisuje obraz wartości, restore przyjmuje go z powrotem. W przykładzie String upraszcza
niezmienność zapamiętanego stanu.

**Przykład:** Editor.save() oddaje niezmienny Snapshot; restore() wraca do tekstu sprzed kolejnej edycji.

**Mechanizm w kodzie:** fragment modelu Java w Markdown. Typy poniżej pokazują współpracę wewnątrz przykładu;
wspólny kontekst znajduje się w [mapie przykładów](../../LABORATORIA.md).

```java
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
```

**Konkretny przypadek użycia:** Przywrócenie roboczego tekstu

- **Wejście:** Dokument przed zmianą ma tekst A, po zmianie tekst AB.
- **Przebieg:** Editor wydaje Snapshot(A), historia zachowuje go, restore odtwarza wartość.
- **Oczekiwany wynik:** Użytkownik wraca do A bez wiedzy historii o prywatnych polach edytora.
- **Błąd lub granica:** Wysłany wcześniej email z AB nie zostaje cofnięty przez przywrócenie pamięci edytora.

**Kiedy zastosować:** Gdy przywracanie lokalnego stanu ma być niezależne od szczegółów jego reprezentacji. Dla
dużych danych porównaj pełne snapshoty z różnicami i trwałymi strukturami, pamiętając o koszcie odtwarzania.

**Koszt i pułapka:** Pełne snapshoty kosztują pamięć O(n) na zapis; zewnętrzne skutki nie wracają wraz ze
stanem edytora.

**Rozwinięcie i ślad wykonania:** save() nie przechowuje żywej referencji do przyszłych zmian edytora, tylko
wartość tekstu z danego momentu. Historia może trzymać kilka Snapshot i wybierać, który przywrócić. Jeśli
snapshot obejmuje kolekcje, kopia musi obejmować cały stan wymagający odtworzenia. Dokument poza edytorem, np.
zapisany plik, nie wróci automatycznie do poprzedniej wersji.

<!-- correctness-example:start -->
**Scenariusz poprawności:** szkic asercji w konwencji JUnit pokazuje oczekiwane zachowanie. Jest treścią
dokumentacji, bez automatycznego uruchamiania w module.

```java
@Test
void mementoRestoresEarlierState() {
    var editor = new Editor();
    editor.write("before");
    var snapshot = editor.save();
    editor.write("after");
    editor.restore(snapshot);
    assertEquals("before", editor.text());
}
```
<!-- correctness-example:end -->

**Ćwiczenie sprawdzające:** Zaprojektuj limit historii i porównaj snapshot z zapisem różnic.

## Observer

**Intuicja:** Producent faktu nie musi znać wszystkich przyszłych reakcji. Może ogłosić zmianę, a
zainteresowane obiekty zapisują się na powiadomienia. Observer oddziela w ten sposób źródło informacji od
sposobów jej wykorzystania: nowy odbiorca dołącza bez dopisywania konkretnego wywołania do kodu producenta.

**Problem i mechanizm:** Obiekt informuje lokalnych subskrybentów o zmianie, zamiast znać ich szczegółowe
działanie.

**Role i współpraca:** Events jest źródłem, Consumer listenerem, a uchwyt AutoCloseable reprezentuje
subskrypcję. Publish wywołuje odbiorców synchronicznie w tym samym procesie. To klient subskrypcji odpowiada
za jej zakończenie we właściwym momencie życia.

**Przykład:** Events.subscribe() zwraca uchwyt wypisania; po close() listener nie dostaje kolejnego eventu.

**Mechanizm w kodzie:** fragment modelu Java w Markdown. Typy poniżej pokazują współpracę wewnątrz przykładu;
wspólny kontekst znajduje się w [mapie przykładów](../../LABORATORIA.md).

```java
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
```

**Konkretny przypadek użycia:** Lokalna aktualizacja paneli

- **Wejście:** Zmiana ceny produktu interesuje licznik podsumowania i podgląd oferty.
- **Przebieg:** Oba obiekty zapisują się jako listenery, źródło publikuje PriceChanged.
- **Oczekiwany wynik:** Dołączenie trzeciego panelu nie zmienia kodu źródła.
- **Błąd lub granica:** Po zamknięciu panelu trzeba wypisać listener; callback nie jest trwałą dostawą po
  restarcie.

**Kiedy zastosować:** Dla lokalnych powiadomień o zmianach i wielu niezależnych reakcji. Gdy reakcje muszą
przetrwać restart lub działać niezależnie czasowo, potrzebny jest dodatkowy trwały transport i kontrakt
dostarczenia.

**Koszt i pułapka:** Przykład jest synchroniczny i nietrwały; wyjątek listenera zatrzymuje dalsze wywołania.
Brak unsubscribe prowadzi do wycieków.

**Rozwinięcie i ślad wykonania:** Subskrypcja zwraca uchwyt zamknięcia, żeby odbiorca kontrolował swój czas
życia. Publish kopiuje listę przed iteracją: wypisanie w trakcie callbacku wpływa dopiero na przyszłe
publikacje. Test powinien więc osobno sprawdzać subskrypcję, dostawę i unsubscribe. Gdy listener rzuci
wyjątek, aktualny wariant przerywa dalsze dostawy; izolację błędów trzeba wprowadzić świadomie.

<!-- correctness-example:start -->
**Scenariusz poprawności:** szkic asercji w konwencji JUnit pokazuje oczekiwane zachowanie. Jest treścią
dokumentacji, bez automatycznego uruchamiania w module.

```java
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
```
<!-- correctness-example:end -->

**Ćwiczenie sprawdzające:** Sprawdź wypisanie i omów różnicę względem trwałego Pub/Sub.

## State

**Intuicja:** Ta sama operacja może znaczyć coś innego zależnie od etapu życia obiektu. Zamówienie nowe można
opłacić, opłacone powinno odrzucić ponowną płatność. State przenosi zachowanie do reprezentacji stanów, dzięki
czemu lista dozwolonych przejść jest widoczna obok zachowania danego etapu, zamiast w wielu rozproszonych
warunkach.

**Problem i mechanizm:** Zachowanie i dozwolone przejścia zależą od aktualnego stanu obiektu.

**Role i współpraca:** Order jest kontekstem przechowującym OrderState. NewOrder po pay zwraca PaidOrder, a
PaidOrder odrzuca kolejne pay. Kontekst podmienia stan dopiero po udanym przejściu i nie udostępnia klientowi
dowolnego settera statusu.

**Przykład:** Order zaczyna w NewOrder; pay() przechodzi do PaidOrder, a drugie pay() jest odrzucane bez
zmiany stanu.

**Mechanizm w kodzie:** fragment modelu Java w Markdown. Typy poniżej pokazują współpracę wewnątrz przykładu;
wspólny kontekst znajduje się w [mapie przykładów](../../LABORATORIA.md).

```java
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
```

**Konkretny przypadek użycia:** Cykl zamówienia

- **Wejście:** Order zaczyna jako NEW, klient wykonuje pay dwukrotnie.
- **Przebieg:** Pierwszy stan wyznacza PAID, drugi odrzuca niedozwolone ponowne przejście.
- **Oczekiwany wynik:** Po pierwszej płatności i po drugim błędzie stan pozostaje PAID.
- **Błąd lub granica:** Sprawdzenie w pamięci nie zabezpiecza dwóch zapisów do DB bez wspólnej wersji.

**Kiedy zastosować:** Gdy operacje i dozwolone przejścia wyraźnie zależą od stanu, zwłaszcza przy rosnącym
lifecycle. Dla małej tabeli prostych przejść enum z walidacją może dać czytelniejszy model niż wiele klas.

**Koszt i pułapka:** Dla kilku prostych przejść enum i tabela bywają czytelniejsze; persystencja i
współbieżność wymagają dodatkowych reguł.

**Rozwinięcie i ślad wykonania:** Order nie ma setState(PAID): jedyna droga prowadzi przez pay() aktualnego
stanu. Najpierw stan oblicza nowe przejście, dopiero potem następuje przypisanie, więc wyjątek nie zmienia
obiektu. Takie przejście w pamięci nadal nie blokuje równoczesnej zmiany utrwalonej wersji; repozytorium musi
egzekwować wersję albo inną granicę transakcji.

<!-- correctness-example:start -->
**Scenariusz poprawności:** szkic asercji w konwencji JUnit pokazuje oczekiwane zachowanie. Jest treścią
dokumentacji, bez automatycznego uruchamiania w module.

```java
@Test
void stateRejectsForbiddenTransitionWithoutChangingState() {
    var order = new Order();
    assertEquals("NEW", order.state());
    order.pay();
    assertThrows(IllegalStateException.class, order::pay);
    assertEquals("PAID", order.state());
}
```
<!-- correctness-example:end -->

**Ćwiczenie sprawdzające:** Dodaj anulowanie tylko przed opłaceniem i test niedozwolonego przejścia.

## Strategy

**Intuicja:** Cena może mieć różne polityki rabatu, a algorytm wysyłki różne sposoby wyboru przewoźnika.
Strategy traktuje tę zmienną decyzję jako zależność. Kod używający wyniku nie musi znać szczegółów każdej
polityki; dostaje funkcję lub obiekt o wspólnym kontrakcie i stosuje go w tym samym miejscu przebiegu.

**Problem i mechanizm:** Wymienny algorytm jest przekazywany jako obiekt lub funkcja z ustalonym kontraktem.

**Role i współpraca:** Price jest klientem algorytmu, LongUnaryOperator strategią, lambda konkretną polityką.
Klient przekazuje dane, strategia zwraca wynik, a price sprawdza niezmiennik końcowej ceny. Wybór strategii
pozostaje poza samym obliczeniem.

**Przykład:** price(1000, cents -> cents * 9 / 10) stosuje rabat 10%, a funkcja tożsamościowa nie daje rabatu.

**Mechanizm w kodzie:** fragment modelu Java w Markdown. Typy poniżej pokazują współpracę wewnątrz przykładu;
wspólny kontekst znajduje się w [mapie przykładów](../../LABORATORIA.md).

```java
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
```

**Konkretny przypadek użycia:** Polityki rabatu sklepu

- **Wejście:** Cena to 1000 groszy; klient zwykły ma brak rabatu, stały 10%.
- **Przebieg:** Przypadek użycia wybiera strategię, price stosuje ją i sprawdza wynik.
- **Oczekiwany wynik:** Wynik wynosi odpowiednio 1000 albo 900, bez zmiany algorytmu checkout.
- **Błąd lub granica:** Strategia zwracająca -1 albo więcej niż cena narusza kontrakt i jest odrzucana.

**Kiedy zastosować:** Gdy kilka algorytmów realizuje tę samą rolę albo chcesz zmieniać politykę bez naruszania
reszty przepływu. Jeśli wariant nigdy nie występuje, dodatkowy interfejs nie zastąpi realnej potrzeby
zmienności.

**Koszt i pułapka:** Strategia musi zachować niezmienniki klienta; nadmierna abstrakcja dla jednej stałej
reguły zwiększa kod.

**Rozwinięcie i ślad wykonania:** Funkcja rabatu ma swobodę implementacji, ale wynik musi mieścić się od zera
do ceny wejściowej. Klient sprawdza tę własność zamiast ufać dowolnej lambdzie. Ten sam kontrakt pozwala
testować różne algorytmy wspólnymi przypadkami. Gdy potrzebne są nazwa polityki, konfiguracja i audyt wyboru,
pełny obiekt strategii bywa czytelniejszy niż anonimowa funkcja.

<!-- correctness-example:start -->
**Scenariusz poprawności:** szkic asercji w konwencji JUnit pokazuje oczekiwane zachowanie. Jest treścią
dokumentacji, bez automatycznego uruchamiania w module.

```java
@Test
void strategyCanBeReplacedAndCannotCreateNegativePrice() {
    assertEquals(900, price(1000, cents -> cents * 9 / 10));
    assertEquals(1000, price(1000, cents -> cents));
    assertThrows(IllegalArgumentException.class, () -> price(1000, cents -> -1));
}
```
<!-- correctness-example:end -->

**Ćwiczenie sprawdzające:** Podaj rabat zwracający ujemną cenę i sprawdź walidację kontraktu.

## Template Method

**Intuicja:** Kilka importerów może mieć wspólne kroki walidacji, normalizacji i wydania wyniku, ale różnić
się parsowaniem. Template Method umieszcza stabilny przebieg w nadklasie i zostawia wskazane kroki do
uzupełnienia. Rozszerzenie nie buduje algorytmu od zera: dopasowuje wyznaczone miejsca wspólnego szablonu.

**Problem i mechanizm:** Nadklasa utrwala kolejność kroków algorytmu, pozostawiając wybrane kroki podklasie.

**Role i współpraca:** Importer jest klasą szkieletu, run metodą szablonową, parse punktem rozszerzenia,
CsvImporter konkretną realizacją kroku. Final w run chroni kolejność, a podklasa nie może pominąć walidacji
przez nadpisanie całego wejścia.

**Przykład:** Importer.run() waliduje i przycina wejście; CsvImporter dostarcza tylko parse().

**Mechanizm w kodzie:** fragment modelu Java w Markdown. Typy poniżej pokazują współpracę wewnątrz przykładu;
wspólny kontekst znajduje się w [mapie przykładów](../../LABORATORIA.md).

```java
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
```

**Konkretny przypadek użycia:** Import dwóch formatów pliku

- **Wejście:** CSV i plik rozdzielany średnikami mają wspólne walidacje wejścia.
- **Przebieg:** Run zawsze waliduje i normalizuje, podklasa uzupełnia wyłącznie parse.
- **Oczekiwany wynik:** Każdy importer odrzuca puste wejście niezależnie od formatu.
- **Błąd lub granica:** Podklasa nie może ominąć szkieletu przez nadpisanie final run.

**Kiedy zastosować:** Gdy kolejność działań pozostaje stała, a zmieniają się nieliczne kroki. Jeżeli klient ma
swobodnie przestawiać etapy, kompozycja strategii lub pipeline lepiej odpowie na taką potrzebę.

**Koszt i pułapka:** Dziedziczenie utrudnia składanie kilku niezależnych wariantów. Strategy składa zachowanie
bez wspólnej klasy bazowej.

**Rozwinięcie i ślad wykonania:** Metoda run() pozostaje final, więc podklasa nie ominie walidacji pustego
wejścia ani kopiowania wyniku. Rozszerzenie dotyczy tylko parse(). To dobry wybór przy stabilnym szkielecie i
niewielkiej liczbie punktów zmiany. Jeśli kolejność kroków ma być konfigurowalna przez klienta, pipeline lub
kompozycja strategii zapewni większą swobodę niż kolejne hooki dziedziczenia.

<!-- correctness-example:start -->
**Scenariusz poprawności:** szkic asercji w konwencji JUnit pokazuje oczekiwane zachowanie. Jest treścią
dokumentacji, bez automatycznego uruchamiania w module.

```java
@Test
void templateMethodKeepsCommonValidation() {
    assertEquals(List.of("a", "b"), new CsvImporter().run(" a,b "));
    assertThrows(IllegalArgumentException.class, () -> new CsvImporter().run(" "));
}
```
<!-- correctness-example:end -->

**Ćwiczenie sprawdzające:** Dodaj importer średnikowy bez pominięcia wspólnej walidacji.

## Visitor

**Intuicja:** Masz stabilny zestaw figur, ale stale dodajesz operacje: pole, eksport, sprawdzanie wymiarów.
Dodawanie każdej operacji do każdej figury rozprasza jedną zmianę po całej hierarchii. Visitor grupuje jedną
operację w osobnym obiekcie, który ma wariant dla każdego znanego typu elementu.

**Problem i mechanizm:** Nowa operacja na stabilnym zbiorze typów trafia do visitora z metodą dla każdego
konkretnego elementu.

**Role i współpraca:** Shape jest elementem z accept, Circle i Rectangle konkretnymi typami, Visitor
kontraktem operacji, Area jej realizacją. Element wybiera właściwe visit(this), więc operacja otrzymuje
konkretny typ bez ręcznych rzutowań klienta.

**Przykład:** Circle i Rectangle wywołują właściwy wariant Area.visit(); klient dostaje pole figury.

**Mechanizm w kodzie:** fragment modelu Java w Markdown. Typy poniżej pokazują współpracę wewnątrz przykładu;
wspólny kontekst znajduje się w [mapie przykładów](../../LABORATORIA.md).

```java
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
```

**Konkretny przypadek użycia:** Eksport i pole figur

- **Wejście:** System zna Circle i Rectangle, potrzebuje Area oraz eksportu tekstowego.
- **Przebieg:** Element wywołuje odpowiednie visit, nowy visitor grupuje nową operację.
- **Oczekiwany wynik:** Eksport można dodać bez zmiany reguł Area i bez rzutowań klienta.
- **Błąd lub granica:** Dodanie Triangle wymaga rozszerzenia wszystkich visitorów; to koszt stabilności
  zestawu typów.

**Kiedy zastosować:** Gdy częściej dodajesz operacje niż nowe rodzaje elementów. Przy dynamicznie rosnącej
hierarchii koszt aktualizacji wszystkich visitorów może przeważyć; dla sealed typów porównaj również jawne
dopasowanie wariantów.

**Koszt i pułapka:** Dodanie nowego elementu zmienia wszystkich visitorów. Sealed hierarchy i pattern matching
to alternatywa dla zamkniętego zbioru.

**Rozwinięcie i ślad wykonania:** Wywołanie accept wybiera implementację konkretnej figury, a ta wywołuje
przeciążone visit(this). W miejscu tego drugiego wywołania typ this jest już konkretny. Dodanie ExportText
wymaga nowego visitora, lecz dodanie Triangle rozszerza kontrakt każdego visitora. Dlatego oceniaj stabilność
zbioru elementów przed wyborem tego wzorca, zamiast kierować się samą liczbą operacji.

<!-- correctness-example:start -->
**Scenariusz poprawności:** szkic asercji w konwencji JUnit pokazuje oczekiwane zachowanie. Jest treścią
dokumentacji, bez automatycznego uruchamiania w module.

```java
@Test
void visitorSelectsOperationForConcreteElement() {
    assertEquals(12.0, new Rectangle(3, 4).accept(new Area()), 0.0001);
    assertEquals(Math.PI, new Circle(1).accept(new Area()), 0.0001);
}
```
<!-- correctness-example:end -->

**Ćwiczenie sprawdzające:** Dodaj eksport do tekstu, a potem trójkąt; porównaj zakres obu zmian.
