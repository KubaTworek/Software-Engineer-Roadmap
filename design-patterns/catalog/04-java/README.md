# Java — idiomy i kompozycja kodu

[Moduł](../../README.md) · [Indeks](../../INDEKS.md) · [Materiały](../../MATERIALY.md#java)

Idiomy i wzorce aplikacyjne. **15 kart.**

**Status przykładów:** moduł dokumentacyjny. Fragmenty Java, SQL i HTTP oraz pseudokod są objaśnieniami
mechanizmu; kompletne modele i scenariusze poprawności również znajdują się w Markdown. Nie ma osobnej
aplikacji, buildu Java ani testów uruchamianych przez Maven.

**Rozbudowany kod i omówienie:** [własność zasobów, skrzynka i Single-flight](../../labs/05-concurrency.md).
Studium w Markdown rozwija wskazany podzbiór wzorców tej kategorii.

**Jak rozumieć tę kategorię:** Tutaj wzorzec nie zawsze wymaga rozbudowanej hierarchii klas. Część rozwiązań
wynika bezpośrednio z możliwości Javy: funkcji przekazywanych jako zależność, niezmiennych wartości, zakresów
try-with-resources czy kontraktów kolekcji. Najpierw ustal, czy problem dotyczy tworzenia, własności zasobu,
reprezentacji błędu czy przepływu wykonania. Dopiero potem dobieraj liczbę typów i narzędzie języka.

## Spis kart

- [Simple Factory / Static Factory Method](#simple-factory--static-factory-method)
- [Dependency Injection / Inversion of Control](#dependency-injection--inversion-of-control)
- [Immutable Object / Defensive Copy](#immutable-object--defensive-copy)
- [Null Object](#null-object)
- [Result / Either / Railway-oriented Programming](#result--either--railway-oriented-programming)
- [Fluent Interface](#fluent-interface)
- [Execute Around / Loan / RAII przez AutoCloseable](#execute-around--loan--raii-przez-autocloseable)
-
  [Lazy Initialization / Initialization-on-demand Holder](#lazy-initialization--initialization-on-demand-holder)
- [Object Pool / Resource Pool](#object-pool--resource-pool)
- [Step Builder / Type-state](#step-builder--type-state)
- [Functional Core / Imperative Shell](#functional-core--imperative-shell)
- [Thread Confinement / Thread-local Storage](#thread-confinement--thread-local-storage)
- [Guarded Suspension / Monitor Object](#guarded-suspension--monitor-object)
- [Producer–Consumer / Bounded Buffer](#producerconsumer--bounded-buffer)
- [Double Dispatch](#double-dispatch)

## Simple Factory / Static Factory Method

**Intuicja:** Nazwa konstruktora jest zawsze taka sama jak klasy, więc nie wyjaśnia, co oznacza kilka
podobnych argumentów. Nazwana fabryka może zakodować jednostkę, wariant lub sposób interpretacji danych. W
odróżnieniu od GoF Factory Method jest to po prostu funkcja tworząca produkt, bez obowiązku dziedziczenia
kreatora.

**Problem i mechanizm:** Nazwana funkcja wybiera lub buduje obiekt; nie wymaga dziedziczenia kreatora.

**Role i współpraca:** ParserFactory wybiera implementację Reader na podstawie formatu. Czytający klient
dostaje Reader i nie musi znać konstruktorów konkretnych parserów. Nieznany format jest jawnym błędem wyboru.

**Przykład:** Money.ofPln(1200) wyraża jednostkę, a parser typu pliku wybiera CsvReader lub JsonReader.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
Reader createReader(String format) {
    return switch (format) {
        case "csv" -> new CsvReader();
        case "json" -> new JsonReader();
        default -> throw new IllegalArgumentException("Unknown format");
    };
}
```

**Konkretny przypadek użycia:** Parser importowanego pliku

- **Wejście:** Pole format ma csv, json albo nieobsługiwane xls.
- **Przebieg:** Nazwana metoda createReader wybiera zgodny Reader w jednym miejscu.
- **Oczekiwany wynik:** Klient czyta przez wspólny kontrakt i nie zna konstruktorów parserów.
- **Błąd lub granica:** Nieznany format jest odrzucony; nie wybieramy przypadkowego parsera domyślnego.

**Kiedy zastosować:** Gdy nazwa tworzenia wyjaśnia dane lub trzeba ukryć wybór podtypu. Nie twórz osobnej
klasy fabryki, jeśli jedna nazwana metoda na produkcie wystarczy.

**Koszt i pułapka:** To nie GoF Factory Method: nie ma nadpisywanej metody w kreatorze. Funkcja może ukrywać
cache i zwracać podtyp.

**Rozwinięcie i ślad wykonania:** Metoda o nazwie ofPln może ustalić walutę i nie dopuścić pomylenia groszy ze
złotymi. Jej kontrakt może też zwrócić podtyp albo istniejącą instancję, czego konstruktor nie wyraża równie
jasno. Nie opieraj klienta na tożsamości wyniku, jeśli fabryka nie gwarantuje tworzenia nowego obiektu.

**Ćwiczenie sprawdzające:** Porównaj konstruktor, statyczną fabrykę i Factory Method dla tego samego produktu.

## Dependency Injection / Inversion of Control

**Intuicja:** Obiekt, który sam tworzy bazę, zegar i klienta HTTP, ukrywa wymagania potrzebne do wykonania
pracy. Dependency Injection dostarcza te zależności z zewnątrz. Dzięki temu tworzenie grafu obiektów jest
osobnym krokiem, a logika use case nie decyduje, czy zapis trafia do pliku, pamięci czy relacyjnej bazy.

**Problem i mechanizm:** Zależności podaje zewnętrzny composition root; obiekt nie wyszukuje ich w globalnym
rejestrze.

**Role i współpraca:** Orders jest portem używanym przez PlaceOrder. Konstruktor otrzymuje implementację, a
composition root wybiera SqlOrders albo adapter testowy. Inversion of Control jest pojęciem szerszym; DI jest
jednym ze sposobów oddania kontroli tworzenia zależności.

**Przykład:** new OrderService(saved::add) wstrzykuje adapter portu repozytorium; test obserwuje zapis bez
kontenera.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
interface Orders {
    void save(Order order);
}
record PlaceOrder(Orders orders) {
    void execute(Order order) {
        orders.save(order);
    }
}
PlaceOrder service = new PlaceOrder(new SqlOrders(dataSource));
```

**Konkretny przypadek użycia:** Potwierdzanie bez infrastruktury

- **Wejście:** Use case ma zapisać order42, w scenariuszu używamy listy zapisanych obiektów.
- **Przebieg:** Composition root wstrzykuje Orders; wykonanie nie tworzy własnego połączenia DB.
- **Oczekiwany wynik:** Widać jeden zapis po poprawnej decyzji i zero po błędzie walidacji.
- **Błąd lub granica:** Globalny Service Locator ukrywałby wymagane zależności i utrudniał kontrolę otoczenia.

**Kiedy zastosować:** Gdy chcesz testować politykę bez infrastruktury albo zmieniać adaptery niezależnie od
reguł. Zacznij od konstruktora; kontener DI nie jest warunkiem użycia wzorca.

**Koszt i pułapka:** DI nie wymaga Springa. Service Locator ukrywa zależności i przesuwa błędy do runtime.

**Rozwinięcie i ślad wykonania:** Composition root tworzy adapter i przekazuje go do konstruktora przed
wykonaniem place(). W teście adapter saved::add ujawnia efekt bez infrastruktury, więc sprawdzamy, że
walidacja następuje przed zapisem. Samo wstrzyknięcie nie decyduje jeszcze o czasie życia zależności ani jej
bezpieczeństwie wątkowym.

**Ćwiczenie sprawdzające:** Podmień adapter na zapis do pliku bez zmiany usługi.

## Immutable Object / Defensive Copy

**Intuicja:** Jeśli kilka fragmentów programu ma referencję do tej samej listy, zmiana przez jeden fragment
może zaskoczyć pozostałe. Immutable Object usuwa tę możliwość dla stanu wartości. Defensive Copy chroni
granicę: obiekt nie przejmuje dowolnej mutowalnej referencji klienta i nie oddaje swojego wewnętrznego
kontenera do modyfikacji.

**Problem i mechanizm:** Po utworzeniu stan nie zmienia się, a mutowalne wejścia i wyjścia są kopiowane
zgodnie z kontraktem.

**Role i współpraca:** Report przyjmuje listę, ale przechowuje jej niezmienną kopię. Elementy String też są
niezmienne, więc nie trzeba kopiować każdego napisu. Record sam nie wykona tego kroku dla przekazanej
kolekcji.

**Przykład:** Request kopiuje headers przez List.copyOf; modyfikacja buildera nie zmienia gotowego requestu.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
record Report(List<String> lines) {
    Report {
        lines = List.copyOf(lines);
    }
}
var input = new ArrayList<>(List.of("first"));
var report = new Report(input);
input.add("second"); // report nadal zawiera tylko first
```

**Konkretny przypadek użycia:** Snapshot raportu

- **Wejście:** Wejściowa lista ma jedną linię; po utworzeniu raportu klient dopisuje drugą.
- **Przebieg:** Konstruktor kopiuje kontener, a napisy są niezmiennymi wartościami.
- **Oczekiwany wynik:** Raport nadal ma jedną linię.
- **Błąd lub granica:** List<StringBuilder> wymagałaby również polityki kopiowania elementów.

**Kiedy zastosować:** Dla wartości, konfiguracji i snapshotów współdzielonych między klientami. Przy dużych
strukturach oceń koszt kopii i możliwość współdzielenia niezmiennych części zamiast kopiowania całego grafu.

**Koszt i pułapka:** record zapewnia finalne pola referencji, a nie głęboką niezmienność elementów kolekcji.

**Rozwinięcie i ślad wykonania:** List.copyOf odcina możliwość zmiany składu kolekcji, lecz zachowuje
referencje do elementów. Dla List<String> to wystarcza, dla List<StringBuilder> już nie. Jawny przykład z
mutowalnym elementem powinien pokazać, czy domena wymaga głębokiej kopii, czy świadomie współdzielonej
referencji.

**Ćwiczenie sprawdzające:** Umieść w rekordzie listę mutowalnych obiektów i pokaż kontrprzykład płytkiej
kopii.

## Null Object

**Intuicja:** Część klientów potrzebuje opcjonalnego działania, np. dodatkowej diagnostyki. Zamiast sprawdzać
null przed każdym wywołaniem, można dostarczyć implementację, której poprawnym zachowaniem jest brak efektu.
Istotne jest znaczenie tego braku: nie wolno w ten sposób udawać sukcesu wymaganej operacji, takiej jak
obowiązkowy audyt.

**Problem i mechanizm:** Brak działania reprezentuje pełnoprawna implementacja kontraktu zamiast rozproszonych
warunków null.

**Role i współpraca:** Audit określa operację, NoAudit ją pomija, a właściwy adapter zapisuje ślad. Klient
zawsze wywołuje ten sam kontrakt. Decyzja o dopuszczalności pustego działania zapada przy konfiguracji.

**Przykład:** NO_AUDIT.record(event) świadomie pomija opcjonalny audyt w lokalnym narzędziu.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
interface Audit {
    void record(String event);
}
Audit disabledAudit = event -> { };
Audit fileAudit = event -> auditFile.append(event);

OrderService service = new OrderService(disabledAudit);
```

**Konkretny przypadek użycia:** Opcjonalny audyt narzędzia lokalnego

- **Wejście:** Tryb diagnostyczny zapisuje zdarzenia, zwykły tryb świadomie je pomija.
- **Przebieg:** Konfiguracja podaje aktywny Audit albo NoAudit; klient zawsze wywołuje record.
- **Oczekiwany wynik:** W kodzie operacji nie pojawiają się wielokrotne sprawdzenia null.
- **Błąd lub granica:** Brak obowiązkowego audytu bezpieczeństwa powinien być błędem, nie pustą implementacją.

**Kiedy zastosować:** Dla semantycznie opcjonalnych dodatków, gdzie brak działania jest legalnym wariantem.
Gdy brak zależności oznacza błąd konfiguracji, zgłoś go zamiast podstawiać cichego zastępcę.

**Koszt i pułapka:** Cichy brak zapisu byłby błędem w audycie wymaganym przez domenę; brak wyniku nie zawsze
oznacza pustą operację.

**Rozwinięcie i ślad wykonania:** Klient zawsze wywołuje record(), a wybór aktywnego lub pustego audytu zapada
w konfiguracji. Dzięki temu nie ma wielu if(audit != null). Warunkiem poprawności jest biznesowa
dopuszczalność braku działania; dla obowiązkowego audytu brak adaptera powinien blokować start albo operację.

**Ćwiczenie sprawdzające:** Wskaż, kiedy NoOpMailSender jest bezpieczny, a kiedy maskuje awarię konfiguracji.

## Result / Either / Railway-oriented Programming

**Intuicja:** Błędny format podany przez użytkownika jest zwykłym wynikiem walidacji, a nie zawsze awarią
programu. Result opisuje sukces i oczekiwane niepowodzenie jako różne warianty danych. Kolejny krok świadomie
wybiera, co robić z błędem; nie musi zgadywać, czy null oznacza brak wyniku, błąd czy niepoprawne użycie API.

**Problem i mechanizm:** Oczekiwany błąd jest wariantem wyniku, przez który jawnie przechodzą kolejne kroki.

**Role i współpraca:** Result jest zamkniętym kontraktem, Ok przenosi wartość, Invalid informację o błędzie.
Pattern matching wymusza rozpatrzenie wariantów. W łańcuchu obliczeń dalszy krok działa tylko na sukcesie.

**Przykład:** positiveInteger("0") daje Failure, a "5" daje Success(5); sealed interface pozwala rozpatrzyć
warianty.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
sealed interface Result permits Ok, Invalid { }
record Ok(int value) implements Result { }
record Invalid(String reason) implements Result { }

String describe(Result result) {
    return switch (result) {
        case Ok ok -> "Accepted: " + ok.value();
        case Invalid bad -> "Rejected: " + bad.reason();
    };
}
```

**Konkretny przypadek użycia:** Walidacja liczby sztuk

- **Wejście:** Wejścia to 5, 0 i abc.
- **Przebieg:** Parser zwraca Success(5), Failure dla wartości niedodatniej i Failure formatu.
- **Oczekiwany wynik:** Klient rozpoznaje oczekiwany błąd bez zgadywania znaczenia null.
- **Błąd lub granica:** Nieoczekiwany błąd programisty nie powinien znikać w ogólnym Failure bez przyczyny.

**Kiedy zastosować:** Dla walidacji i oczekiwanych odrzuceń, które klient ma normalnie obsłużyć. Nie ukrywaj
nieoczekiwanych wyjątków programistycznych w ogólnym Failure bez przyczyny i diagnostyki.

**Koszt i pułapka:** Nie opakowuj awarii programisty tak, by zniknął stack trace. Java nie ma w JDK
uniwersalnego Either.

**Rozwinięcie i ślad wykonania:** Success i Failure tworzą zamknięty zestaw wariantów. Kolejny krok przetwarza
wartość wyłącznie po sukcesie, a po błędzie zachowuje jego opis. Rozróżnij błąd danych użytkownika od
nieoczekiwanego wyjątku programisty; nie zamieniaj każdego Throwable w ogólne „niepowodzenie”, tracąc
diagnostykę.

**Ćwiczenie sprawdzające:** Dodaj walidację górnego limitu bez wyjątków dla błędnych danych użytkownika.

## Fluent Interface

**Intuicja:** API może prowadzić czytelnika przez intencję: wybierz źródło, ustaw warunki, wykonaj odczyt.
Fluent Interface organizuje nazwy i zwracane typy, aby takie wyrażenie było zrozumiałe jako całość. Sam
łańcuch wywołań nie rozstrzyga, czy obiekt jest mutowalny, tworzy kopie, czy wykonuje skutki już podczas
konfiguracji.

**Problem i mechanizm:** API czyta się jak spójne wyrażenie dzięki łańcuchom metod i typom wyrażającym kolejne
etapy.

**Role i współpraca:** Query zbiera parametry, kolejne metody zwracają konfigurację, fetch jest operacją
końcową. Czytelnik powinien wiedzieć, który krok rzeczywiście kontaktuje się z bazą i może zawieść.

**Przykład:** query.where(active).orderBy(name).limit(10) opisuje intencję zapytania; builder jest jednym z
możliwych zastosowań.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
List<OrderSummary> result = query
        .forCustomer(customerId)
        .withStatus(PAID)
        .orderedByCreationTime()
        .limit(20)
        .fetch(); // Dopiero tutaj wykonujemy odczyt.
```

**Konkretny przypadek użycia:** Zapytanie listy zamówień

- **Wejście:** Klient wybiera PAID, porządek czasu i limit 20.
- **Przebieg:** Łańcuch zbiera konfigurację, końcowe fetch wykonuje odczyt.
- **Oczekiwany wynik:** Miejsce I/O jest widoczne i można je ograniczyć jednym kontraktem czasu.
- **Błąd lub granica:** Współdzielenie mutowalnego buildera zapytania między requestami może mieszać filtry.

**Kiedy zastosować:** Gdy sekwencja konfiguracji czyta się wyraźniej niż wiele osobnych wywołań. Unikaj
długich łańcuchów ukrywających różne operacje I/O i trudnych do przerwania podczas diagnostyki.

**Koszt i pułapka:** Długie łańcuchy utrudniają debugowanie, a mutowalny receiver może być omyłkowo
współdzielony.

**Rozwinięcie i ślad wykonania:** Łańcuch zwracający this działa na tym samym mutowalnym obiekcie, a łańcuch
zwracający nowe wartości buduje kolejne snapshoty. Dla czytelnika to ważniejsze niż sam wygląd kropek.
Dokumentuj, czy wykonanie następuje od razu, czy dopiero przez terminalne build(), execute() albo collect().

**Ćwiczenie sprawdzające:** Zaprojektuj API uniemożliwiające limit(-1), bez mylenia fluent z budowaniem
produktu.

## Execute Around / Loan / RAII przez AutoCloseable

**Intuicja:** Każdy klient zasobu może zapomnieć zamknąć go po wyjątku. Execute Around przenosi wspólny
początek i koniec do jednej funkcji: otwórz, wykonaj przekazaną pracę, zamknij. Klient opisuje środek
operacji, a właściciel zakresu gwarantuje zakończenie zgodnie z kontraktem zasobu.

**Problem i mechanizm:** Jedna funkcja kontroluje nabycie i zwolnienie zasobu oraz wykonuje kod klienta w tym
zakresie.

**Role i współpraca:** Session jest zasobem AutoCloseable, open dostawcą, work callbackiem. Try-with-resources
wywołuje close po sukcesie i błędzie. Loan oznacza tu czasowe udostępnienie, nie przeniesienie zasobu do
dowolnie długiego życia klienta.

**Przykład:** writeAndClose(session, values) stosuje try-with-resources; wyjątek zapisu nadal zamyka sesję.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
<T> T withSession(Function<Session, T> work) {
    try (Session session = sessions.open()) {
        return work.apply(session);
    }
}
Order order = withSession(session -> session.load(orderId));
```

**Konkretny przypadek użycia:** Zakres sesji plikowej

- **Wejście:** Zapis drugiej linii rzuca wyjątek.
- **Przebieg:** Funkcja withSession otwiera zasób, wykonuje callback i zamyka go w try-with-resources.
- **Oczekiwany wynik:** Sesja jest zamknięta także po błędzie środka operacji.
- **Błąd lub granica:** Referencja wyniesiona z callbacku nie daje prawa używania zamkniętego zasobu.

**Kiedy zastosować:** Dla plików, sesji i wypożyczeń, których cykl życia powtarza się w wielu miejscach.
Kontrakt callbacku powinien zabraniać używania zasobu po zakończeniu zakresu.

**Koszt i pułapka:** RAII w C++ wiąże się z czasem życia obiektu; w Javie deterministyczne zamknięcie wymaga
zakresu try. GC go nie zastępuje.

**Rozwinięcie i ślad wykonania:** Zasób ma jednego właściciela zakresu: funkcja przyjmuje go, wykonuje
callback i zamyka w try-with-resources. Jeśli callback i close rzucą wyjątek, Java zachowuje błąd close jako
suppressed przy pierwotnym błędzie. Klient nie powinien używać zasobu po wyjściu z tego zakresu.

**Ćwiczenie sprawdzające:** Sprawdź close() po błędzie i wyjątek close jako suppressed exception.

## Lazy Initialization / Initialization-on-demand Holder

**Intuicja:** Kosztowny obiekt nie musi powstawać, jeśli żaden klient go nie użyje. Lazy Initialization odsuwa
ten koszt do pierwszego dostępu. Dla jednej statycznej instancji holder wykorzystuje gwarancje inicjalizacji
klasy: JVM inicjalizuje klasę pomocniczą przy pierwszym aktywnym użyciu i publikuje wynik zgodnie z regułami
języka.

**Problem i mechanizm:** Kosztowna wartość powstaje dopiero przy pierwszym użyciu; holder korzysta z
inicjalizacji klasy JVM.

**Role i współpraca:** Outer udostępnia get, Holder przechowuje finalną instancję. Zwykłe użycie innych metod
Outer nie musi inicjalizować Holder. Kolejne get odczytują tę samą wartość, nie wykonują ponownie
inicjalizacji.

**Przykład:** Statyczna klasa Holder ma final Client; get() odwołuje się do Holder.INSTANCE dopiero na
żądanie.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
final class SharedClient {
    private static final class Holder {
        static final Client INSTANCE = new Client(configuration());
    }
    static Client get() {
        return Holder.INSTANCE;
    }
}
```

**Konkretny przypadek użycia:** Opcjonalny klient eksportu

- **Wejście:** Aplikacja startuje, ale tylko 1 z 100 requestów korzysta z eksportu.
- **Przebieg:** Holder inicjalizuje Client przy pierwszym get i publikuje jedną instancję w zakresie klasy.
- **Oczekiwany wynik:** Nie płacimy kosztu inicjalizacji w przebiegach niekorzystających z klienta.
- **Błąd lub granica:** Odświeżanie konfiguracji i retry po błędzie inicjalizacji wymagają innego lifecycle.

**Kiedy zastosować:** Gdy wartość jest kosztowna, opcjonalna i jednorazowa w tym zakresie. Odświeżanie, retry
inicjalizacji i wiele konfiguracji wymagają innego modelu niż statyczny holder.

**Koszt i pułapka:** Wariant dla jednej statycznej wartości nie rozwiązuje cyklicznych zależności, odświeżania
ani retry po błędzie inicjalizacji klasy.

**Rozwinięcie i ślad wykonania:** Inicjalizacja klasy Holder jest synchronizowana przez JVM i publikuje jej
statyczne pola przed użyciem. To rozwiązuje jednorazową publikację, ale nie odświeżanie konfiguracji. Wariant
z mutowalnym polem i double-checked locking wymaga volatile; bez niego obserwacja referencji nie ustanawia
poprawnie całego kontraktu publikacji.

**Ćwiczenie sprawdzające:** Porównaj holder, synchronized getter i niepoprawne double-checked locking bez
volatile.

## Object Pool / Resource Pool

**Intuicja:** Niektóre zasoby są kosztowne lub muszą mieć ograniczoną liczbę, np. połączenia z bazą. Pool
utrzymuje określoną liczbę instancji i wypożycza je na czas pracy. Kluczowe staje się nie samo tworzenie, lecz
prawo do wyłącznego używania oraz poprawny zwrot po zakończeniu.

**Problem i mechanizm:** Ograniczona liczba drogich zasobów jest wypożyczana i zwracana, zamiast tworzenia na
każde użycie.

**Role i współpraca:** ResourcePool zarządza wolnymi zasobami, Lease prawem czasowego użycia, klient zakresem
pracy. Timeout kończy czekanie na slot. Close oddaje slot dokładnie raz, ale samo w sobie nie resetuje
zabrudzonego zasobu.

**Przykład:** Pula 10 połączeń DB wymusza timeout pozyskania; try-with-resources oddaje połączenie do puli.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
var pool = new ResourcePool<>(connections);
try (var lease = pool.acquire(Duration.ofMillis(50))) {
    Connection connection = lease.value();
    connection.execute(statement);
} // lease wraca także po wyjątku w execute.
```

**Konkretny przypadek użycia:** Pula dwóch zasobów

- **Wejście:** Dwa lease są zajęte, trzeci klient czeka najwyżej 50 ms.
- **Przebieg:** Acquire pobiera wolny slot, close oddaje go raz, timeout kończy oczekiwanie.
- **Oczekiwany wynik:** Trzeci klient dostaje zasób po zwrocie albo jawny timeout.
- **Błąd lub granica:** Podwójne close nie może zwiększyć pojemności; brudny stan zasobu wymaga osobnej
  polityki.

**Kiedy zastosować:** Dla ograniczonych, drogich zasobów z jasnym cyklem wypożyczenia. Dla drobnych DTO zwykła
alokacja Javy zwykle jest prostsza; pooling trzeba uzasadnić pomiarem i polityką czyszczenia stanu.

**Koszt i pułapka:** Pool zwykłych krótkotrwałych DTO często zwiększa retencję i synchronizację. Zwrócony
zasób wymaga resetu stanu.

**Rozwinięcie i ślad wykonania:** ResourcePool wypożycza tę samą instancję tylko jednemu aktywnemu lease.
Timeout dotyczy oczekiwania na wolny slot, nie całej pracy po pozyskaniu. Dwukrotne close nie może oddać
zasobu dwa razy. Dodatkowo określ, kto resetuje brudny stan i usuwa uszkodzony zasób, bo sama kolejka tego nie
zapewnia.

**Ćwiczenie sprawdzające:** Wyczerp pulę i opisz limit kolejki, timeout oraz wykrywanie wycieków.

## Step Builder / Type-state

**Intuicja:** Zwykły builder dopuszcza wywołanie build przed ustawieniem wymaganych pól, a błąd wychodzi
dopiero w runtime. Step Builder zapisuje dozwoloną kolejność w typach. Każdy etap udostępnia tylko następne
legalne operacje, więc klient nie może nawet wywołać metody, której nie ma w bieżącym stanie konfiguracji.

**Problem i mechanizm:** Typ zwracany przez krok API dopuszcza tylko operacje legalne w danym etapie budowy.

**Role i współpraca:** RecipientStep prowadzi do BodyStep, ten do Ready. Konkretna implementacja może
przechowywać dane wspólnie, ale klient widzi ograniczony interfejs aktualnego etapu. Poprawność wartości nadal
wymaga walidacji runtime.

**Przykład:** start().recipient("a").body("b").send() nie udostępnia send() przed recipient i body.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
interface RecipientStep { BodyStep to(String address); }
interface BodyStep { Ready body(String text); }
interface Ready { Message build(); }

Message message = builder.start()
        .to("reader@example.test")
        .body("Hello")
        .build();
```

**Konkretny przypadek użycia:** Budowanie wiadomości

- **Wejście:** Wymagane są odbiorca i treść przed build.
- **Przebieg:** RecipientStep udostępnia BodyStep, a dopiero Ready udostępnia build.
- **Oczekiwany wynik:** Pominięcie etapu nie ma legalnej metody w bieżącym interfejsie.
- **Błąd lub granica:** Pusty email nadal wymaga walidacji runtime; kolejność typów nie waliduje treści.

**Kiedy zastosować:** Gdy kilka wymaganych kroków ma stabilną kolejność, a błąd ich pominięcia jest częsty.
Wiele niezależnych pól opcjonalnych może stworzyć zbyt dużo typów etapów.

**Koszt i pułapka:** Liczba interfejsów rośnie wraz z kombinacjami pól; prosty builder z walidacją bywa
tańszy.

**Rozwinięcie i ślad wykonania:** Interfejs kroku recipient udostępnia body, ale jeszcze nie send. Po body
klient otrzymuje typ końcowy z send. Dzięki temu pominięcie etapu jest błędem kompilacji. Walidacja wartości,
np. pustego adresata, nadal wymaga kontroli runtime i nie znika przez samą sekwencję typów.

**Ćwiczenie sprawdzające:** Zaprojektuj dwa wymagane etapy i pokaż przykład, który nie przechodzi kompilacji.

## Functional Core / Imperative Shell

**Intuicja:** Najtrudniej testować kod, który miesza decyzję, aktualizację stanu, odczyt zegara i wysyłkę.
Functional Core oddziela czyste obliczenie od tych efektów. Rdzeń otrzymuje komplet potrzebnych danych i
zwraca decyzję; shell odpowiada za pozyskanie danych oraz atomowe zastosowanie wyniku we właściwym świecie
zewnętrznym.

**Problem i mechanizm:** Czysta funkcja podejmuje decyzję i zwraca dane, a osobna warstwa wykonuje I/O.

**Role i współpraca:** Decide jest czystą funkcją, Decision opisem zmiany, shell klientem portów I/O. Wersja
odczytanego stanu wiąże decyzję z zapisem, żeby współbieżna zmiana nie została zignorowana.

**Przykład:** decide(available) zwraca Decision z eventami; shell zapisuje je przez port dopiero po decyzji.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
Snapshot current = orders.load(orderId);
Decision decision = decide(current, command, fixedNow);

orders.commit(
        current.version(),
        decision.newState(),
        decision.events());
```

**Konkretny przypadek użycia:** Decyzja rezerwacji

- **Wejście:** Snapshot ma available=2 i version=7, komenda żąda 1 sztuki.
- **Przebieg:** Czysta funkcja wylicza stan available=1 i zamiar eventu, shell zapisuje z expectedVersion=7.
- **Oczekiwany wynik:** Powtórzenie obliczenia daje tę samą decyzję bez podwójnej wysyłki.
- **Błąd lub granica:** Zmiana wersji przed zapisem wymaga ponownej decyzji na świeżym stanie.

**Kiedy zastosować:** Dla złożonych reguł, które chcesz powtarzać, porównywać i testować bez infrastruktury.
Nie przenoś gwarancji atomowości na czystość funkcji: shell nadal musi rozstrzygnąć konflikt wersji.

**Koszt i pułapka:** Czystość nie rozwiązuje wyścigu między odczytem stanu a zapisem decyzji; potrzebna jest
kontrola wersji lub transakcja.

**Rozwinięcie i ślad wykonania:** decide() zwraca listę zamiarów, które shell zapisuje albo wykonuje później.
Ta sama para stanu i komendy daje ten sam wynik, więc łatwo testować niezmienniki. Gdy stan zmieni się przed
zapisem, shell musi odrzucić starą wersję i ponownie wyliczyć decyzję na aktualnych danych.

**Ćwiczenie sprawdzające:** Powtórz tę samą decyzję i sprawdź ten sam wynik; opisz atomowy zapis.

## Thread Confinement / Thread-local Storage

**Intuicja:** Brak współdzielenia mutowalnego stanu bywa prostszy niż jego blokowanie. Thread Confinement
przypisuje go jednemu wątkowi lub wywołaniu. ThreadLocal przechowuje osobną wartość dla wątku, ale w puli ten
sam wątek obsługuje różne żądania, więc jego zakres może być dłuższy niż zakres pojedynczego klienta.

**Problem i mechanizm:** Mutowalny stan ma jednego właściciela wykonania, co usuwa potrzebę współdzielenia go
między wątkami.

**Role i współpraca:** Wątek jest właścicielem kopii, ThreadLocal rejestrem kontekstu, finally miejscem
zakończenia użycia. Kod asynchroniczny może działać na innym wątku i nie odziedziczyć oczekiwanego kontekstu.

**Przykład:** Parser ma lokalny StringBuilder na żądanie; kontekst ThreadLocal usuwa się w finally przy puli
wątków.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
try {
    tenantContext.set(verifiedTenant);
    service.handle(request);
} finally {
    tenantContext.remove(); // Wątek puli obsłuży później innego klienta.
}
```

**Konkretny przypadek użycia:** Kontekst tenantów w puli

- **Wejście:** Jeden worker obsługuje najpierw tenant A, potem B.
- **Przebieg:** Kontekst jest ustawiany na czas wywołania i usuwany w finally.
- **Oczekiwany wynik:** B nie dziedziczy A po poprzednim zadaniu.
- **Błąd lub granica:** ThreadLocal nie przenosi się automatycznie do pracy async na innym wątku.

**Kiedy zastosować:** Gdy stan rzeczywiście należy do jednego wykonawcy. Dla danych pojedynczego requestu
preferuj jawny parametr, jeśli propagacja przez async jest istotna i ThreadLocal ukrywa zależność.

**Koszt i pułapka:** ThreadLocal nie przenosi automatycznie kontekstu do async; virtual threads zmieniają
koszt liczby takich wartości.

**Rozwinięcie i ślad wykonania:** Lokalny StringBuilder ma właściciela stosu konkretnego wywołania.
ThreadLocal ma właściciela wątku, który w puli wykonuje wiele niezwiązanych żądań. To różne zakresy: brak
remove w finally może udostępnić stan następnemu zadaniu, mimo że nigdy nie pracują równocześnie.

**Ćwiczenie sprawdzające:** Pokaż przeciek kontekstu między dwoma zadaniami w tej samej puli.

## Guarded Suspension / Monitor Object

**Intuicja:** Konsument pustej kolejki nie ma jeszcze nic do zrobienia, ale nie chce bez końca zużywać CPU na
sprawdzanie. Guarded Suspension pozwala czekać na warunek stanu pod monitorem. Po przebudzeniu trzeba ponownie
sprawdzić warunek, ponieważ sygnał nie rezerwuje elementu dla konkretnego czekającego.

**Problem i mechanizm:** Wątek czeka na warunek pod monitorem i sprawdza go ponownie w pętli po przebudzeniu.

**Role i współpraca:** Queue jest stanem, monitor chroni warunek i zmianę, wait zwalnia monitor na czas
czekania. Producent zmienia kolejkę pod tym samym monitorem i sygnalizuje oczekującym; konsument wraca do
kontroli w while.

**Przykład:** take() wykonuje while(queue.isEmpty()) wait(); put() zmienia stan pod tym samym lockiem i
notifyAll().

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
synchronized (lock) {
    while (queue.isEmpty() && !closed) {
        lock.wait();
    }
    if (queue.isEmpty()) {
        throw new IllegalStateException("Queue closed");
    }
    return queue.remove();
}
```

**Konkretny przypadek użycia:** Konsument pustej kolejki

- **Wejście:** Dwaj konsumenci czekają, producent dodaje jeden element.
- **Przebieg:** Wait zwalnia monitor, notify budzi, każdy ponownie sprawdza warunek w while.
- **Oczekiwany wynik:** Tylko jeden konsument pobiera element, drugi nadal czeka albo kończy zgodnie z closed.
- **Błąd lub granica:** If zamiast while pozwala drugiemu działać na już pustej kolejce.

**Kiedy zastosować:** Dla jawnego czekania na warunek współdzielonego stanu. W typowym kodzie wybieraj gotowe
BlockingQueue lub inne narzędzia JDK, jeśli już realizują potrzebny kontrakt.

**Koszt i pułapka:** Sygnał nie jest stanem, możliwe są spurious wakeups; trzeba obsłużyć przerwanie i
zakończenie producentów.

**Rozwinięcie i ślad wykonania:** wait zwalnia monitor, więc inny wątek może zmienić kolejkę przed ponownym
uzyskaniem locka przez oczekującego. Dlatego przebudzenie nie uprawnia bezwarunkowo do take; trzeba ponownie
sprawdzić warunek w while. Stan zamknięcia kolejki również musi być częścią warunku kończącego oczekiwanie.

**Ćwiczenie sprawdzające:** Wyjaśnij, dlaczego if zamiast while jest błędne przy dwóch konsumentach.

## Producer–Consumer / Bounded Buffer

**Intuicja:** Producent i odbiorca często pracują z różną prędkością. Kolejka oddziela moment przyjęcia
zadania od momentu wykonania, ale bez limitu może jedynie zamienić przeciążenie w brak pamięci. Bounded Buffer
wyznacza maksymalną liczbę oczekujących elementów i wymusza decyzję: poczekaj, odrzuć albo zastosuj inną
politykę.

**Problem i mechanizm:** Ograniczona kolejka oddziela tempo tworzenia zadań od ich wykonania.

**Role i współpraca:** BlockingQueue jest buforem, producent publikuje, konsument pobiera. Offer z timeout
określa zachowanie przy pełnym buforze, take przy pustym. Osobny kontrakt końca pracy pozwala bezpiecznie
zatrzymać obie strony.

**Przykład:** ArrayBlockingQueue(100) i dwaj pracownicy ograniczają backlog; offer z timeoutem umożliwia
odrzucenie pracy.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
BlockingQueue<Job> queue = new ArrayBlockingQueue<>(100);
boolean accepted = queue.offer(job, 20, TimeUnit.MILLISECONDS);
if (!accepted) {
    throw new RejectedExecutionException("Backlog full");
}
// Inny wykonawca:
process(queue.take());
```

**Konkretny przypadek użycia:** Ograniczony import w tle

- **Wejście:** Producent tworzy 500 zadań, bufor mieści 100.
- **Przebieg:** Offer ma termin, konsumenci wykonują pracę w swoim tempie.
- **Oczekiwany wynik:** Nieprzyjęte zadania mają jawny wynik zamiast nieograniczonego wzrostu pamięci.
- **Błąd lub granica:** Stały napływ większy od wydajności wymaga admission control, nie tylko większej
  kolejki.

**Kiedy zastosować:** Dla przetwarzania w tle i wygładzania krótkich burstów. Jeśli stały napływ przewyższa
wydajność, kolejka nie wystarczy: potrzebny limit przyjęć lub większa zdolność wykonania.

**Koszt i pułapka:** Nieograniczona kolejka zamienia przeciążenie w OOM. Koniec pracy wymaga kontraktu
lifecycle i anulowania.

**Rozwinięcie i ślad wykonania:** Pojemność bufora wyznacza maksymalny backlog, a offer z timeoutem pozwala
odrzucić nadmiar przed OOM. W BoundedMailbox komenda ma Future wyniku, więc odrzucenie jest obserwowalne. Przy
zamknięciu ustal, czy przyjęte zadania mają zostać opróżnione, czy anulowane z ukończeniem ich Future.

**Ćwiczenie sprawdzające:** Zaprojektuj shutdown bez utraty zadań i bez wiecznie czekającego konsumenta.

## Double Dispatch

**Intuicja:** W Javie nadpisanie zależy od rzeczywistego typu odbiorcy, ale przeciążenie metod jest wybierane
na podstawie typów znanych w miejscu wywołania. Double Dispatch wykorzystuje dwa kolejne wywołania, aby
decyzja zależała od dwóch obiektów. To mechanizm, na którym opiera się klasyczny Visitor.

**Problem i mechanizm:** Wybór zachowania zależy od dwóch typów runtime, zwykle przez dwa wywołania
polimorficzne.

**Role i współpraca:** Shape.accept wybiera konkretny element, następnie visitor.visit(this) wybiera operację
dla tego elementu. W kodzie Circle typ this jest konkretny, więc nie potrzeba zgadywać go przez instanceof w
kliencie.

**Przykład:** Shape.accept(visitor) wywołuje visitor.visit(this); przeciążenie zostaje rozstrzygnięte dla
konkretnej figury.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
interface Visitor { String visit(Circle circle); }
record Circle(int radius) {
    String accept(Visitor visitor) {
        return visitor.visit(this); // this ma tutaj typ Circle.
    }
}
```

**Konkretny przypadek użycia:** Wybór operacji dla figury

- **Wejście:** Zmienna Shape wskazuje Circle, visitor ma warianty visit.
- **Przebieg:** Accept uruchamia kod Circle, który przekazuje konkretne this do visit.
- **Oczekiwany wynik:** Wybrana zostaje operacja dla Circle bez instanceof w kliencie.
- **Błąd lub granica:** Samo przeciążone visit(shape) wybiera wariant według statycznego typu argumentu.

**Kiedy zastosować:** Gdy współpraca zależy od pary typów i stabilny zbiór elementów uzasadnia drugi krok
wywołania. Dla prostego zamkniętego modelu jawny switch może lepiej pokazywać wszystkie przypadki.

**Koszt i pułapka:** Samo przeciążenie metod zależy od typu statycznego argumentu. Visitor wykorzystuje double
dispatch, ale nie jest jego jedyną formą.

**Rozwinięcie i ślad wykonania:** Przeciążenie visit(Shape) i visit(Circle) jest wybierane według typu
argumentu w miejscu wywołania. Gdy zmienna ma typ Shape, sam obiekt Circle nie wystarczy do wybrania
visit(Circle). Dopiero accept przenosi wywołanie do kodu, w którym this ma konkretny typ.

**Ćwiczenie sprawdzające:** Porównaj visit(shape) i shape.accept(visitor), gdy zmienna Shape zawiera Circle.
