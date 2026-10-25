# GoF — wzorce strukturalne

[Moduł](../../README.md) · [Indeks](../../INDEKS.md) · [Materiały](../../MATERIALY.md#gof)

Wzorce obiektowe. **7 kart.**

**Status przykładów:** moduł dokumentacyjny. Fragmenty Java, SQL i HTTP oraz pseudokod są objaśnieniami
mechanizmu; kompletne modele i scenariusze poprawności również znajdują się w Markdown. Nie ma osobnej
aplikacji, buildu Java ani testów uruchamianych przez Maven.

**Jak rozumieć tę kategorię:** Te wzorce opisują, jak łączyć obiekty, nie zmieniając niepotrzebnie ich
wnętrza. Podobnie wyglądające opakowania mają różne cele: Adapter tłumaczy kontrakt, Decorator dokłada
zachowanie, Proxy kontroluje dostęp, a Facade upraszcza podsystem. Composite porządkuje strukturę drzewiastą,
Bridge rozdziela osie zmiany, Flyweight współdzieli część danych. Rozpoznawaj intencję i własność stanu, nie
samą obecność interfejsu oraz delegowania.

## Spis kart

- [Adapter](#adapter)
- [Bridge](#bridge)
- [Composite](#composite)
- [Decorator](#decorator)
- [Facade](#facade)
- [Flyweight](#flyweight)
- [Proxy](#proxy)

## Adapter

**Intuicja:** Masz komponent, który robi potrzebną rzecz, lecz jego metody i dane nie pasują do oczekiwań
twojego kodu. Przepisywanie klientów przywiązałoby ich do dostawcy, a modyfikacja starego komponentu może być
niemożliwa. Adapter tworzy granicę tłumaczenia, dzięki której klient nadal używa swojego języka i własnego
kontraktu.

**Problem i mechanizm:** Tłumaczy kontrakt istniejącego komponentu na port oczekiwany przez klienta.

**Role i współpraca:** Payments jest portem klienta, LegacyBank zastanym komponentem, BankAdapter
pośrednikiem. Adapter przyjmuje kwotę w kontrakcie klienta, waliduje ją, tłumaczy reprezentację i wywołuje
bank. Tłumaczenie błędów również powinno pozostać na tej granicy.

**Przykład:** BankAdapter przyjmuje long w groszach i przekazuje LegacyBank tekst w groszach, zachowując
jednostkę i walidację.

**Mechanizm w kodzie:** fragment modelu Java w Markdown. Typy poniżej pokazują współpracę wewnątrz przykładu;
wspólny kontekst znajduje się w [mapie przykładów](../../LABORATORIA.md).

```java
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
```

**Konkretny przypadek użycia:** Integracja starego operatora płatności

- **Wejście:** Domena przekazuje 1250 groszy; biblioteka oczekuje tekstu w groszach i własnych wyjątków.
- **Przebieg:** Adapter waliduje kwotę, tłumaczy 1250 na tekst i mapuje błąd dostawcy na kontrakt portu.
- **Oczekiwany wynik:** Domena otrzymuje wynik bez importowania SDK operatora.
- **Błąd lub granica:** Dzielenie kwoty przez 100 byłoby tu błędem jednostek, mimo poprawnych typów.

**Kiedy zastosować:** Przy integracji starej biblioteki, zewnętrznego API lub innego modelu danych. Jeśli oba
komponenty mają już zgodny kontrakt, dodatkowy adapter nie daje korzyści samym faktem istnienia.

**Koszt i pułapka:** Tłumaczenie typów nie usuwa różnicy semantyki, zaokrągleń i błędów dostawcy.

**Rozwinięcie i ślad wykonania:** Klient przekazuje 1250 groszy. Adapter waliduje dodatniość i wywołuje
charge("1250"), więc zmienia zapis, a nie jednostkę. Gdyby bank oczekiwał złotych, potrzebne byłoby jawne
przeliczenie z regułą precyzji. Zapisz również mapowanie błędu dostawcy na błąd portu; surowy wyjątek jego SDK
nie powinien bez potrzeby przenikać do domeny.

<!-- correctness-example:start -->
**Scenariusz poprawności:** szkic asercji w konwencji JUnit pokazuje oczekiwane zachowanie. Jest treścią
dokumentacji, bez automatycznego uruchamiania w module.

```java
@Test
void adapterPreservesUnitsAndRejectsInvalidAmount() {
    var payments = new BankAdapter(new LegacyBank());
    assertEquals("charged:1250", payments.pay(1250));
    assertThrows(IllegalArgumentException.class, () -> payments.pay(-1));
}
```
<!-- correctness-example:end -->

**Ćwiczenie sprawdzające:** Sprawdź 1250 groszy oraz kwotę ujemną; opisz mapowanie błędów banku.

## Bridge

**Intuicja:** Gdy powiadomienia dzielą się na alerty i potwierdzenia, a kanały na email i SMS, dziedziczenie
wszystkich kombinacji tworzy mnożącą się listę klas. Bridge zauważa, że są tu dwa niezależne pytania: jakie
znaczenie ma wiadomość oraz jak ją dostarczyć. Odpowiedzi łączy przez kompozycję zamiast kolejnymi podklasami
każdej pary.

**Problem i mechanizm:** Oddziela abstrakcję od implementacji, gdy obie mają niezależnie ewoluować.

**Role i współpraca:** Notification przechowuje Channel. Alert i Receipt odpowiadają za znaczenie treści,
implementacja Channel za wysyłkę. Jeden alert może użyć dowolnego zgodnego kanału, a jeden kanał obsługiwać
wiele rodzajów powiadomień.

**Przykład:** Alert i Receipt korzystają z Channel; email i SMS można łączyć z oboma rodzajami wiadomości bez
czterech podklas.

**Mechanizm w kodzie:** fragment modelu Java w Markdown. Typy poniżej pokazują współpracę wewnątrz przykładu;
wspólny kontekst znajduje się w [mapie przykładów](../../LABORATORIA.md).

```java
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
```

**Konkretny przypadek użycia:** Alerty i potwierdzenia trzema kanałami

- **Wejście:** Istnieją Alert i Receipt oraz kanały email, SMS, push.
- **Przebieg:** Znaczenie wiadomości jest w abstrakcji, a wybrany Channel realizuje sposób dostarczenia.
- **Oczekiwany wynik:** Dodanie push nie wymaga klas AlertPush i ReceiptPush ani zmian pozostałych
  powiadomień.
- **Błąd lub granica:** Limit znaków SMS nadal wymaga jawnej polityki, nie cichego obcięcia znaczącej treści.

**Kiedy zastosować:** Gdy dwie osie rzeczywiście ewoluują niezależnie i nowe kombinacje mają sens. Dla jednej
osi zmiany Strategy albo prosta zależność wystarczy; Bridge nie wymaga tworzenia sztucznej macierzy wariantów.

**Koszt i pułapka:** Dodatkowa indirection nie opłaca się przy jednej osi zmiany. Adapter dopasowuje zastane
API, Bridge organizuje dwie osie projektu.

**Rozwinięcie i ślad wykonania:** Alert i Receipt definiują znaczenie wiadomości, Channel sposób dostarczenia.
Nowy typ powiadomienia nie wymaga dopisywania wariantu dla każdego kanału, a nowy kanał nie dotyka obu typów.
Gdy na konkretny kanał obowiązują inne limity treści, określ je w kontrakcie lub osobnej polityce, zamiast
ukrywać niezgodność w kombinacji podklas.

<!-- correctness-example:start -->
**Scenariusz poprawności:** szkic asercji w konwencji JUnit pokazuje oczekiwane zachowanie. Jest treścią
dokumentacji, bez automatycznego uruchamiania w module.

```java
@Test
void bridgeCombinesTwoIndependentDimensions() {
    Channel email = text -> "email:" + text;
    Channel sms = text -> "sms:" + text;
    assertEquals("email:ALERT outage", new Alert(email).send("outage"));
    assertEquals("sms:ALERT outage", new Alert(sms).send("outage"));
    assertEquals("sms:RECEIPT paid", new Receipt(sms).send("paid"));
}
```
<!-- correctness-example:end -->

**Ćwiczenie sprawdzające:** Dodaj kanał push bez zmian Alert i Receipt.

## Composite

**Intuicja:** Koszyk może zawierać pojedynczy produkt oraz zestaw innych produktów. Jeśli klient rozpoznaje
każdy typ osobnymi warunkami, sam implementuje przechodzenie po całej strukturze. Composite pozwala
potraktować element i grupę przez wspólny kontrakt, a odpowiedzialność za zejście do dzieci zostawić grupie.

**Problem i mechanizm:** Liście i złożenia implementują wspólny kontrakt, dzięki czemu operacja działa na
całym drzewie.

**Role i współpraca:** Item jest wspólną operacją, Product liściem, Bundle węzłem z dziećmi. Klient woła price
na korzeniu. Grupa pyta każde dziecko o cenę i sumuje wyniki, nie wiedząc, czy dziecko też jest grupą.

**Przykład:** Bundle zawiera Product i kolejne Bundle; price() rekurencyjnie sumuje ceny również pustego
zestawu.

**Mechanizm w kodzie:** fragment modelu Java w Markdown. Typy poniżej pokazują współpracę wewnątrz przykładu;
wspólny kontekst znajduje się w [mapie przykładów](../../LABORATORIA.md).

```java
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
```

**Konkretny przypadek użycia:** Zestaw produktów zagnieżdżony w zestawie

- **Wejście:** Koszyk zawiera produkt 100 oraz zestaw produktów 200 i 300 jednostek.
- **Przebieg:** Operacja price schodzi po wspólnym Item przez węzły Bundle do liści.
- **Oczekiwany wynik:** Cena całości wynosi 600; pusta grupa daje zero.
- **Błąd lub granica:** Cykl w strukturze nie może prowadzić do niekończącej się rekurencji; model drzewa
  powinien go wykluczać.

**Kiedy zastosować:** Dla naturalnych drzew: menu, komponentów UI, katalogów i zestawów. Gdy część operacji
nie ma sensu na liściu, nie wymuszaj identycznego szerokiego interfejsu tylko dla pozornej jednolitości.

**Koszt i pułapka:** Cykle, bardzo głębokie drzewa i niejasne współdzielenie węzłów mogą złamać założenia
rekurencji.

**Rozwinięcie i ślad wykonania:** Dla Bundle(Product(100), Bundle(Product(200))) wywołanie schodzi do dwóch
liści i wraca z sumą 300. Koszt jest liniowy względem odwiedzonych węzłów, a stos zależy od wysokości drzewa.
Kopia listy chroni skład grupy, lecz nie gwarantuje niezmienności każdego Item. Dla grafu z cyklami ta sama
rekurencja wymaga wykrywania odwiedzin albo zakazu cykli.

<!-- correctness-example:start -->
**Scenariusz poprawności:** szkic asercji w konwencji JUnit pokazuje oczekiwane zachowanie. Jest treścią
dokumentacji, bez automatycznego uruchamiania w module.

```java
@Test
void compositeHandlesNestedAndEmptyGroups() {
    Item tree = new Bundle(List.of(new Product(100), new Bundle(List.of(new Product(200)))));
    assertEquals(300, tree.price());
    assertEquals(0, new Bundle(List.of()).price());
}
```
<!-- correctness-example:end -->

**Ćwiczenie sprawdzające:** Policz zagnieżdżony zestaw oraz koszt przejścia O(n); ustal, kto zapobiega cyklom.

## Decorator

**Intuicja:** Chcesz dopisywać cechy do obiektu w różnych kombinacjach: kompresję, formatowanie, pomiar czasu.
Dziedziczenie dla każdej kombinacji szybko się rozrasta. Decorator daje obiektowi zewnętrzne opakowanie o tym
samym kontrakcie; kilka opakowań można złożyć w określonej kolejności, bez zmiany klasy źródłowej.

**Problem i mechanizm:** Opakowanie zachowuje interfejs i dokłada zachowanie przez delegację do obiektu
wewnętrznego.

**Role i współpraca:** Text jest kontraktem, PlainText źródłem, Brackets i Uppercase dekoratorami
przechowującymi kolejny Text. Każde opakowanie deleguje wywołanie, a potem zmienia wynik lub dodaje działanie
wokół wywołania.

**Przykład:** Brackets(Uppercase(PlainText)) daje [HELLO] bez zmiany źródłowego tekstu.

**Mechanizm w kodzie:** fragment modelu Java w Markdown. Typy poniżej pokazują współpracę wewnątrz przykładu;
wspólny kontekst znajduje się w [mapie przykładów](../../LABORATORIA.md).

```java
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
```

**Konkretny przypadek użycia:** Opcjonalne cechy odpowiedzi tekstowej

- **Wejście:** Źródło zwraca hello, klient chce wielkie litery i nawiasy.
- **Przebieg:** Uppercase opakowuje źródło, Brackets opakowuje wynik poprzedniego dekoratora.
- **Oczekiwany wynik:** Odpowiedź wynosi [HELLO], a oryginalne źródło nadal zwraca hello.
- **Błąd lub granica:** Dla limitu długości i nagłówka kolejność zmienia wynik; konfiguracja łańcucha jest
  częścią zachowania.

**Kiedy zastosować:** Gdy klient potrzebuje wielu kombinacji niezależnych dodatków. Jeśli jeden dodatek jest
obowiązkową częścią każdego obiektu, bezpośrednia implementacja może być bardziej przejrzysta niż długa lista
opakowań.

**Koszt i pułapka:** Kolejność dekoratorów może zmienić wynik, logowanie wyjątków i liczbę retry. Długi
łańcuch utrudnia diagnostykę.

**Rozwinięcie i ślad wykonania:** Wywołanie idzie od najbardziej zewnętrznego opakowania do źródła, a wynik
wraca przez opakowania w przeciwną stronę. To wyjaśnia znaczenie kolejności: limit tekstu przed dodaniem
nagłówka obejmuje inne znaki niż limit po nim. W kodzie I/O analogicznie retry wokół audytu zapisze kilka
śladów, a audyt wokół retry może opisać jedną operację logiczną.

<!-- correctness-example:start -->
**Scenariusz poprawności:** szkic asercji w konwencji JUnit pokazuje oczekiwane zachowanie. Jest treścią
dokumentacji, bez automatycznego uruchamiania w module.

```java
@Test
void decoratorComposesWithoutChangingOriginal() {
    Text original = new PlainText("hello");
    assertEquals("[HELLO]", new Brackets(new Uppercase(original)).read());
    assertEquals("hello", original.read());
}
```
<!-- correctness-example:end -->

**Ćwiczenie sprawdzające:** Zbuduj dekoratory limitu długości i nagłówka; pokaż nieprzemienność ich
kolejności.

## Facade

**Intuicja:** Użycie podsystemu często wymaga znajomości kilku klas i właściwej kolejności ich metod.
Powielanie tej sekwencji w każdym kliencie prowadzi do rozbieżności. Facade daje nazwane wejście do typowego
scenariusza, tak aby klient wyrażał zamiar, a szczegóły współpracy pozostały wewnątrz granicy podsystemu.

**Problem i mechanizm:** Udostępnia uproszczone wejście do kilku współpracujących części podsystemu.

**Role i współpraca:** Checkout jest fasadą, Inventory i Shipping współpracownikami. Buy rezerwuje towar i
tworzy etykietę. Klient zna jedną operację, ale fasada nadal musi jawnie określić skutki częściowego
powodzenia; nie staje się przez to menedżerem transakcji.

**Przykład:** Checkout.buy() rezerwuje produkt, a potem tworzy etykietę; klient zna jedną metodę.

**Mechanizm w kodzie:** fragment modelu Java w Markdown. Typy poniżej pokazują współpracę wewnątrz przykładu;
wspólny kontekst znajduje się w [mapie przykładów](../../LABORATORIA.md).

```java
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
```

**Konkretny przypadek użycia:** Jedno wejście procesu zakupu

- **Wejście:** Klient zna order42, podsystemy wymagają reserve i createLabel.
- **Przebieg:** Checkout ukrywa kolejność: najpierw rezerwacja, potem etykieta.
- **Oczekiwany wynik:** Klient nie kopiuje tej sekwencji w każdym kontrolerze.
- **Błąd lub granica:** Błąd etykiety po reserve zostawia rezerwację; fasada sama nie oferuje rozproszonego
  rollback.

**Kiedy zastosować:** Gdy wspólny scenariusz wymaga powtarzalnej sekwencji kilku operacji. Zachowaj bardziej
szczegółowe API tam, gdzie klienci potrzebują innych scenariuszy; fasada nie musi przejmować wszystkich
możliwości podsystemu.

**Koszt i pułapka:** Facade może rozrosnąć się w god object. Nie zapewnia atomowości inventory i shipping.

**Rozwinięcie i ślad wykonania:** Fasada redukuje wiedzę klienta o kolejności reserve i label. Test sprawdza,
że błąd reserve blokuje label, ale przy błędzie label rezerwacja już istnieje. W jednym lokalnym store można
użyć transakcji; przy niezależnych właścicielach danych potrzebna jest rezerwacja, kompensacja lub proces
naprawy. Nie dopisuj ukrytej gwarancji atomowości do samej nazwy Checkout.

<!-- correctness-example:start -->
**Scenariusz poprawności:** szkic asercji w konwencji JUnit pokazuje oczekiwane zachowanie. Jest treścią
dokumentacji, bez automatycznego uruchamiania w module.

```java
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
```
<!-- correctness-example:end -->

**Ćwiczenie sprawdzające:** Zasymuluj błąd etykiety po rezerwacji i zaproponuj sposób naprawy.

## Flyweight

**Intuicja:** Milion znaków tekstu nie potrzebuje miliona osobnych kopii tego samego opisu czcionki. Flyweight
dzieli dane na część powtarzaną oraz kontekst konkretnego wystąpienia. Powtarzana część jest współdzielona, a
pozycja i inne cechy indywidualne pozostają przy wystąpieniu lub są podawane podczas operacji.

**Problem i mechanizm:** Współdzieli niezmienny stan wewnętrzny wielu drobnych obiektów, pozostawiając
kontekst na zewnątrz.

**Role i współpraca:** GlyphStyle jest współdzielonym, niezmiennym stanem. Styles wyszukuje równy styl w puli,
Glyph przechowuje znak i pozycję. Dwa Glyph mogą mieć różne współrzędne, wskazując ten sam obiekt stylu.

**Przykład:** Glyph A na pozycji 0 i Glyph B na pozycji 10 mają ten sam GlyphStyle z puli.

**Mechanizm w kodzie:** fragment modelu Java w Markdown. Typy poniżej pokazują współpracę wewnątrz przykładu;
wspólny kontekst znajduje się w [mapie przykładów](../../LABORATORIA.md).

```java
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
```

**Konkretny przypadek użycia:** Edytor miliona znaków

- **Wejście:** Milion wystąpień używa tylko 12 kombinacji fontu i rozmiaru.
- **Przebieg:** Pula zwraca wspólne GlyphStyle, każdy Glyph przechowuje własny znak i współrzędne.
- **Oczekiwany wynik:** Nie tworzymy miliona kopii tych samych 12 stylów.
- **Błąd lub granica:** Mutowalny styl zmieniłby wiele wystąpień; pula unikalnych stylów wymaga limitu
  pamięci.

**Kiedy zastosować:** Gdy istnieje bardzo wiele wystąpień, a liczba wariantów wspólnych danych jest mała.
Najpierw zmierz rozmiar i powtarzalność; pula prawie unikalnych danych może zużywać więcej pamięci niż osobne
wartości.

**Koszt i pułapka:** Pula może rosnąć bez limitu; mutowalny współdzielony styl zmieni wiele znaków. Cache
wyników to inny cel.

**Rozwinięcie i ślad wykonania:** Kluczem puli jest para font i size, więc dwa równe klucze zwracają tę samą
instancję stylu. Pozycja x i znak są częścią Glyph, ponieważ ich współdzielenie zmieniałoby znaczenie
wystąpień. Oszczędność wynika z liczby powtarzanych stylów, nie liczby wszystkich znaków. Przy prawie samych
unikalnych stylach mapa zwiększa pamięć zamiast ją zmniejszać.

<!-- correctness-example:start -->
**Scenariusz poprawności:** szkic asercji w konwencji JUnit pokazuje oczekiwane zachowanie. Jest treścią
dokumentacji, bez automatycznego uruchamiania w module.

```java
@Test
void flyweightSharesIntrinsicStateOnly() {
    var styles = new Styles();
    var a = new Glyph('A', 0, styles.get("mono", 12));
    var b = new Glyph('B', 10, styles.get("mono", 12));
    assertSame(a.style(), b.style());
    assertNotEquals(a.x(), b.x());
    assertNotSame(a.style(), styles.get("mono", 14));
}
```
<!-- correctness-example:end -->

**Ćwiczenie sprawdzające:** Porównaj tożsamość stylów, a potem policz liczbę stylów i wystąpień znaków.

## Proxy

**Intuicja:** Obiekt może być kosztowny do utworzenia, zdalny albo dostępny tylko po sprawdzeniu uprawnień.
Proxy podstawia w jego miejsce obiekt o tym samym kontrakcie i kontroluje moment lub warunki delegacji. Klient
nadal wykonuje znaną operację, lecz dostęp do właściwego obiektu przebiega przez określoną politykę.

**Problem i mechanizm:** Zastępca kontroluje dostęp do obiektu o tym samym kontrakcie: opóźnia, autoryzuje lub
kieruje wywołanie.

**Role i współpraca:** Image jest kontraktem, docelowy obraz źródłem danych, LazyImage zastępcą. Supplier
uruchamia ładowanie przy pierwszym pixels. Po sukcesie proxy zachowuje cel, a przy kolejnych wywołaniach
deleguje bez ponownej inicjalizacji.

**Przykład:** LazyImage uruchamia loader dopiero przy pixels() i używa załadowanego obrazu przy kolejnym
odczycie.

**Mechanizm w kodzie:** fragment modelu Java w Markdown. Typy poniżej pokazują współpracę wewnątrz przykładu;
wspólny kontekst znajduje się w [mapie przykładów](../../LABORATORIA.md).

```java
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
```

**Konkretny przypadek użycia:** Miniatury w długiej liście

- **Wejście:** Ekran ma 200 obrazów, użytkownik otwiera 5 z nich.
- **Przebieg:** LazyImage przechowuje loader i inicjalizuje cel przy pierwszym odczycie pikseli.
- **Oczekiwany wynik:** Pozostałych 195 obrazów nie jest w tym przebiegu ładowanych.
- **Błąd lub granica:** Przy wielu wątkach trzeba osobno zapewnić jedną inicjalizację; identyczny interfejs
  tego nie gwarantuje.

**Kiedy zastosować:** Gdy opóźnienie inicjalizacji albo kontrola dostępu ma konkretną wartość. Dla wywołań
zdalnych ujawnij timeout i niepewny wynik, ponieważ zgodny interfejs nie usuwa różnicy między pamięcią a
siecią.

**Koszt i pułapka:** Model jest jednowątkowy. Remote Proxy wprowadza sieć; identyczny interfejs nie oznacza
lokalnych gwarancji.

**Rozwinięcie i ślad wykonania:** Przed pierwszym pixels() target jest null i nie ma kosztu loadera. Po
sukcesie kolejne wywołania delegują do tej samej instancji; po wyjątku target pozostaje null, więc dopuszczamy
kolejną próbę. W wariancie współbieżnym dwa wątki mogłyby uruchomić loader jednocześnie. Należy wtedy ustalić
jeden mechanizm inicjalizacji i politykę zapamiętywania błędu.

<!-- correctness-example:start -->
**Scenariusz poprawności:** szkic asercji w konwencji JUnit pokazuje oczekiwane zachowanie. Jest treścią
dokumentacji, bez automatycznego uruchamiania w module.

```java
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
```
<!-- correctness-example:end -->

**Ćwiczenie sprawdzające:** Sprawdź brak ładowania przed użyciem i jedną inicjalizację; rozważ wyjątek
loadera.
