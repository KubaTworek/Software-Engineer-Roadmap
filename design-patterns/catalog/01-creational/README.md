# GoF — wzorce kreacyjne

[Moduł](../../README.md) · [Indeks](../../INDEKS.md) · [Materiały](../../MATERIALY.md#gof)

Wzorce obiektowe. **5 kart.**

**Status przykładów:** moduł dokumentacyjny. Fragmenty Java, SQL i HTTP oraz pseudokod są objaśnieniami
mechanizmu; kompletne modele i scenariusze poprawności również znajdują się w Markdown. Nie ma osobnej
aplikacji, buildu Java ani testów uruchamianych przez Maven.

**Jak rozumieć tę kategorię:** Wzorce kreacyjne porządkują moment powstawania obiektów. Nie chodzi o unikanie
każdego new, lecz o ustalenie, kto zna klasę produktu, kto wybiera wariant oraz kto pilnuje poprawności
konfiguracji. Fabryki zmieniają sposób wyboru produktów, Builder organizuje ich składanie, Prototype kopiuje
już przygotowany stan, a Singleton ogranicza zakres instancji. Porównuj te decyzje osobno: obiekt może powstać
przez Builder bez bycia singletonem i bez rodziny fabryk.

## Spis kart

- [Factory Method](#factory-method)
- [Abstract Factory](#abstract-factory)
- [Builder](#builder)
- [Prototype](#prototype)
- [Singleton](#singleton)

## Factory Method

**Intuicja:** Wyobraź sobie wspólną procedurę wysyłki: przygotuj transport, przekaż paczkę, potwierdź nadanie.
Gdy w jej środku zapiszesz new Truck, procedura zna już konkretny środek transportu. Factory Method zostawia
przebieg w klasie bazowej, a decyzję o rodzaju tworzonego obiektu oddaje wyspecjalizowanej podklasie.

**Problem i mechanizm:** Nadklasa definiuje przebieg operacji, a podklasa wybiera konkretny produkt przez
nadpisanie metody fabrykującej.

**Role i współpraca:** Logistics jest kreatorem z algorytmem ship, Transport kontraktem produktu, a
RoadLogistics i SeaLogistics konkretnymi kreatorami. Klient wybiera kreatora przy konfiguracji; później sam
algorytm posługuje się jedynie kontraktem produktu.

**Przykład:** RoadLogistics.ship() korzysta z Transport; RoadLogistics tworzy ciężarówkę, SeaLogistics statek.
Klient wywołuje ten sam algorytm dostawy.

**Mechanizm w kodzie:** fragment modelu Java w Markdown. Typy poniżej pokazują współpracę wewnątrz przykładu;
wspólny kontekst znajduje się w [mapie przykładów](../../LABORATORIA.md).

```java
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
```

**Konkretny przypadek użycia:** Import wyciągów dwóch banków

- **Wejście:** Bank A wysyła CSV, bank B XML; oba importy mają walidację i zapis.
- **Przebieg:** Kreator banku wybiera parser, wspólny algorytm importu odczytuje i waliduje rekordy.
- **Oczekiwany wynik:** Dodanie banku C wymaga nowego kreatora, bez warunku w całym procesie importu.
- **Błąd lub granica:** Nieznany format jest odrzucony przed zapisem; fabryka nie zastępuje walidacji danych.

**Kiedy zastosować:** Gdy biblioteka udostępnia stabilny przebieg, a rozszerzenia mają wybierać jego produkty.
Jeśli zmienia się tylko jedna funkcja tworząca, wstrzyknięty Supplier może wystarczyć bez hierarchii podklas.

**Koszt i pułapka:** Dziedziczenie wiąże wybór produktu z hierarchią kreatorów. Zwykła funkcja Supplier bywa
prostsza.

**Rozwinięcie i ślad wykonania:** W ship() decyzja o produkcie zapada przy wirtualnym wywołaniu
createTransport(), po czym klient widzi wyłącznie Transport. Dlatego dodanie RailLogistics nie wymaga nowego
warunku w algorytmie. Porównaj to z funkcją create(type): tam lista produktów zwykle mieści się w jednym
switchu, a nie w podklasach kreatora.

<!-- correctness-example:start -->
**Scenariusz poprawności:** szkic asercji w konwencji JUnit pokazuje oczekiwane zachowanie. Jest treścią
dokumentacji, bez automatycznego uruchamiania w module.

```java
@Test
void factoryMethodSelectsProductWithoutChangingAlgorithm() {
    assertEquals("truck", new RoadLogistics().ship());
    assertEquals("ship", new SeaLogistics().ship());
}
```
<!-- correctness-example:end -->

**Ćwiczenie sprawdzające:** Dodaj kolej bez modyfikowania ship(); porównaj z prostą fabryką ze switchem.

## Abstract Factory

**Intuicja:** W aplikacji z motywami nie wystarczy utworzyć dowolnego przycisku: przycisk i checkbox powinny
należeć do tej samej rodziny wizualnej. Gdy klient tworzy je niezależnie, łatwo pomieszać warianty. Abstract
Factory przenosi wybór całej zgodnej rodziny do jednego obiektu, a klient prosi go o kolejne rodzaje
produktów.

**Problem i mechanizm:** Jeden kontrakt tworzy spójną rodzinę współpracujących produktów bez wskazywania ich
klas klientowi.

**Role i współpraca:** Widgets jest fabryką rodziny. Button i Checkbox są odrębnymi kontraktami produktów,
DarkWidgets i LightWidgets ich konkretnymi zestawami. Funkcja screen zna kontrakty, ale nie klasy produktów
ani szczegóły ich powstawania.

**Przykład:** DarkWidgets dostarcza ciemny Button i Checkbox; screen() może dostać LightWidgets bez zmiany
kodu ekranu.

**Mechanizm w kodzie:** fragment modelu Java w Markdown. Typy poniżej pokazują współpracę wewnątrz przykładu;
wspólny kontekst znajduje się w [mapie przykładów](../../LABORATORIA.md).

```java
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
```

**Konkretny przypadek użycia:** Spójny zestaw sterowników archiwum

- **Wejście:** Instalacja wybiera lokalny dysk albo object store; potrzebne są Writer i LinkFactory.
- **Przebieg:** Jedna fabryka środowiska dostarcza oba zgodne produkty używane przez eksport raportu.
- **Oczekiwany wynik:** Raport zapisany w object store dostaje poprawny rodzaj linku do tego samego backendu.
- **Błąd lub granica:** Pomieszanie lokalnego Writer z chmurowym LinkFactory jest błędem konfiguracji rodziny.

**Kiedy zastosować:** Gdy przełączasz kilka współpracujących produktów naraz, np. rodzinę sterowników lub
elementów UI. Dla jednego niezależnego produktu zwykła fabryka będzie czytelniejsza; spójność rodziny nie
wymaga wtedy osobnej abstrakcji.

**Koszt i pułapka:** Dodanie nowego rodzaju produktu zmienia wszystkie fabryki. Rodzina jest umową projektu,
nie magiczną gwarancją typów.

**Rozwinięcie i ślad wykonania:** Fabryka stanowi granicę konfiguracji całej rodziny. screen(new
DarkWidgets()) nie potrzebuje argumentu dark w każdej metodzie produktu; ten wybór zapadł raz przy składaniu
zależności. Kiedy dochodzi trzeci produkt Slider, kontrakt Widgets i obie implementacje muszą zostać
rozszerzone. To koszt stabilnej liczby rodzajów produktów i zmiennej liczby rodzin.

<!-- correctness-example:start -->
**Scenariusz poprawności:** szkic asercji w konwencji JUnit pokazuje oczekiwane zachowanie. Jest treścią
dokumentacji, bez automatycznego uruchamiania w module.

```java
@Test
void abstractFactoryKeepsProductFamilyConsistent() {
    assertEquals(List.of("dark-button", "dark-checkbox"), screen(new DarkWidgets()));
    assertEquals(List.of("light-button", "light-checkbox"), screen(new LightWidgets()));
}
```
<!-- correctness-example:end -->

**Ćwiczenie sprawdzające:** Dodaj drugi motyw i sprawdź, że ekran nie miesza rodzin.

## Builder

**Intuicja:** Długi konstruktor z wieloma podobnymi argumentami utrudnia rozpoznanie, co właściwie tworzysz.
Kolejne settery pozwalają natomiast udostępnić obiekt przed zakończeniem konfiguracji. Builder daje osobne
miejsce na zbieranie danych, a gotowy produkt wydaje dopiero w jednym wyraźnym punkcie, kiedy można sprawdzić
wszystkie warunki wspólnie.

**Problem i mechanizm:** Budowanie złożonego obiektu oddziela się od jego końcowej reprezentacji i walidacji.

**Role i współpraca:** RequestBuilder jest roboczą konfiguracją, Request gotowym produktem. Metody path i
header zmieniają konfigurację, build przekazuje ją do walidującego konstruktora. Kopiowanie kolekcji odcina
późniejsze zmiany buildera od wcześniej wydanego produktu.

**Przykład:** RequestBuilder ustawia ścieżkę i nagłówki, build() waliduje ścieżkę oraz kopiuje listę;
późniejsza zmiana buildera nie zmienia Request.

**Mechanizm w kodzie:** fragment modelu Java w Markdown. Typy poniżej pokazują współpracę wewnątrz przykładu;
wspólny kontekst znajduje się w [mapie przykładów](../../LABORATORIA.md).

```java
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
```

**Konkretny przypadek użycia:** Budowanie zlecenia eksportu

- **Wejście:** Eksport ma format CSV, 4 kolumny, filtr dat i opcjonalny adres powiadomienia.
- **Przebieg:** Builder zbiera ustawienia; build sprawdza wymagane pola i kopiuje listę kolumn.
- **Oczekiwany wynik:** Gotowe zlecenie nie zmienia się po dopisaniu kolumny do ponownie użytego buildera.
- **Błąd lub granica:** Sprzeczne daty i brak formatu nie mogą wydać częściowo poprawnego produktu.

**Kiedy zastosować:** Gdy pól opcjonalnych, wariantów konfiguracji lub etapów tworzenia jest dużo. Dla
krótkiego, jednoznacznego konstruktora Builder może tylko zwiększyć liczbę typów bez poprawy zrozumiałości.

**Koszt i pułapka:** Fluent API samo w sobie nie oznacza Buildera. Przy dwóch wymaganych polach konstruktor
jest tańszy.

**Rozwinięcie i ślad wykonania:** Builder ma stan tymczasowy, Request ma stan końcowy. Po pierwszym build()
dopisanie nagłówka modyfikuje tylko builder, ponieważ konstruktor produktu wykonał kopię. Walidacja w
produkcie chroni również klienta, który ominął builder. Przy drogim produkcie oddziel walidację od alokacji
zewnętrznych zasobów, żeby nie pozostawiać ich po nieudanej budowie.

<!-- correctness-example:start -->
**Scenariusz poprawności:** szkic asercji w konwencji JUnit pokazuje oczekiwane zachowanie. Jest treścią
dokumentacji, bez automatycznego uruchamiania w module.

```java
@Test
void builderValidatesAndDoesNotLeakMutableState() {
    var builder = new RequestBuilder().path("/orders").header("Accept: json");
    var request = builder.build();
    builder.header("X-Trace: 1");
    assertEquals(List.of("Accept: json"), request.headers());
    assertThrows(UnsupportedOperationException.class, () -> request.headers().add("oops"));
    assertThrows(IllegalArgumentException.class, () -> new RequestBuilder().path("orders").build());
}
```
<!-- correctness-example:end -->

**Ćwiczenie sprawdzające:** Spróbuj zbudować niepoprawny request i zmodyfikować wcześniejszy produkt przez
builder.

## Prototype

**Intuicja:** Czasem potrzebujesz obiektu podobnego do już istniejącego: dokumentu na podstawie szablonu albo
figury z ustalonym stylem. Odtwarzanie wszystkich ustawień od zera powiela wiedzę o konfiguracji. Prototype
pozwala poprosić istniejący obiekt o kopię, ale wymaga jasnej odpowiedzi, które części stanu mają być
niezależne.

**Problem i mechanizm:** Nowy obiekt powstaje przez skopiowanie skonfigurowanego wzorca zamiast odtwarzania
całego procesu inicjalizacji.

**Role i współpraca:** Document jest jednocześnie nośnikiem danych i prototypem udostępniającym copy. Nowy
dokument ma nową listę akapitów, natomiast niezmienne napisy mogą być współdzielone. Klient wybiera szablon,
zamiast znać cały algorytm jego inicjalizacji.

**Przykład:** Document.copy() kopiuje listę akapitów. Dopisanie akapitu do kopii nie zmienia dokumentu
źródłowego.

**Mechanizm w kodzie:** fragment modelu Java w Markdown. Typy poniżej pokazują współpracę wewnątrz przykładu;
wspólny kontekst znajduje się w [mapie przykładów](../../LABORATORIA.md).

```java
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
```

**Konkretny przypadek użycia:** Kopie szablonu oferty

- **Wejście:** Szablon ma 8 sekcji, wspólny styl i listę indywidualnych uwag.
- **Przebieg:** Copy tworzy niezależne sekcje mutowalne, zachowując wspólny niezmienny opis stylu.
- **Oczekiwany wynik:** Dopisanie uwagi do oferty klienta A nie zmienia szablonu i oferty B.
- **Błąd lub granica:** Płytka kopia listy nie wystarczy, jeśli same uwagi są mutowalnymi obiektami.

**Kiedy zastosować:** Gdy koszt lub liczba ustawień początkowych uzasadniają kopiowanie gotowej konfiguracji.
Jeśli najważniejsza jest walidacja nowych danych, zwykła fabryka bywa prostsza niż rozbudowana polityka
kopiowania grafu.

**Koszt i pułapka:** Kopia płytka pozostawia wspólne mutowalne elementy; głęboka wymaga polityki dla cykli i
tożsamości. clone() nie jest wymagane.

**Rozwinięcie i ślad wykonania:** Skopiowanie Document wymaga wskazania granicy własności: nowa lista, ale te
same niezmienne String są wystarczające. Gdy akapit zacznie zawierać mutowalne komentarze, dwie listy nadal
będą wskazywały wspólne komentarze. Kopiowanie całego grafu powinno określić obsługę cykli i obiektów celowo
współdzielonych, zamiast bezwarunkowo klonować każdą referencję.

<!-- correctness-example:start -->
**Scenariusz poprawności:** szkic asercji w konwencji JUnit pokazuje oczekiwane zachowanie. Jest treścią
dokumentacji, bez automatycznego uruchamiania w module.

```java
@Test
void prototypeSeparatesMutableCollections() {
    var original = new Document(List.of("intro"));
    var copy = original.copy();
    copy.append("new");
    assertEquals(List.of("intro"), original.paragraphs());
    assertEquals(List.of("intro", "new"), copy.paragraphs());
}
```
<!-- correctness-example:end -->

**Ćwiczenie sprawdzające:** Zastąp String mutowalnym akapitem i wskaż, co trzeba skopiować głębiej.

## Singleton

**Intuicja:** Jeżeli wszystkie fragmenty aplikacji mają korzystać z tego samego obiektu, trzeba ustalić
zarówno sposób jego utworzenia, jak i zakres tej wspólności. Singleton łączy ograniczenie liczby instancji z
punktem dostępu. „Jeden” oznacza jednak wskazany zakres, np. classloader, a nie wszystkie serwery i procesy
świata.

**Problem i mechanizm:** Kontroluje pojedynczą instancję i punkt dostępu w określonym zakresie wykonania.

**Role i współpraca:** Configuration.INSTANCE jest instancją zarządzaną przez mechanizm enum JVM. Klienci
odwołują się do niej bez tworzenia nowego obiektu. Enum upraszcza jednorazową inicjalizację, ale nie zarządza
późniejszą synchronizacją dowolnego mutowalnego stanu.

**Przykład:** Configuration.INSTANCE jest stałą enum bez mutowalnego stanu; test porównuje tożsamość instancji
w jednym classloaderze.

**Mechanizm w kodzie:** fragment modelu Java w Markdown. Typy poniżej pokazują współpracę wewnątrz przykładu;
wspólny kontekst znajduje się w [mapie przykładów](../../LABORATORIA.md).

```java
// SINGLETON: enum zapewnia instancję w obrębie classloadera; nie tworzy blokady między procesami.
public enum Configuration {
    INSTANCE;

    // Brak setterów jest tu celowy: jedna instancja nie oznacza bezpiecznej mutacji wielu wątków.
    public String applicationName() {
        return "pattern-lab";
    }
}
```

**Konkretny przypadek użycia:** Niezmienna tablica kodów aplikacji

- **Wejście:** Jeden classloader korzysta z niezmiennego mapowania 50 kodów na opisy.
- **Przebieg:** Jednorazowa inicjalizacja publikuje obiekt, kolejne wywołania odczytują tę samą instancję.
- **Oczekiwany wynik:** Nie budujemy identycznej tablicy dla każdego requestu.
- **Błąd lub granica:** Dwie JVM nadal mają dwa obiekty; singleton nie koordynuje wspólnego stanu klastra.

**Kiedy zastosować:** Gdy pojedynczy, niezmienny obiekt naprawdę ma wspólny zakres i nie wymaga różnych
konfiguracji klientów. W wielu aplikacjach jeden obiekt tworzony w composition root i wstrzykiwany jawnie daje
prostsze testowanie.

**Koszt i pułapka:** Globalny stan utrudnia testy. Singleton Springa ma zakres kontenera; enum nie zapewnia
jednej instancji w klastrze ani synchronizacji metod.

**Rozwinięcie i ślad wykonania:** Porównanie assertSame sprawdza tożsamość w jednym zakresie ładowania klas.
Nie mierzy liczby instancji w klastrze ani nie ustanawia happens-before dla późniejszych zmian pól. Jeśli
konfiguracja jest niezmienna, zwykły obiekt wstrzyknięty do klientów może dać tę samą oszczędność tworzenia
bez globalnego punktu dostępu i z łatwiejszą podmianą w teście.

<!-- correctness-example:start -->
**Scenariusz poprawności:** szkic asercji w konwencji JUnit pokazuje oczekiwane zachowanie. Jest treścią
dokumentacji, bez automatycznego uruchamiania w module.

```java
@Test
void singletonReturnsSameInstanceWithinClassloader() {
    assertSame(Configuration.INSTANCE, Configuration.valueOf("INSTANCE"));
}
```
<!-- correctness-example:end -->

**Ćwiczenie sprawdzające:** Wyjaśnij zachowanie dwóch classloaderów i dwóch procesów JVM.
