# Wzorce testów i utrzymania poprawności

[Moduł](../../README.md) · [Indeks](../../INDEKS.md) · [Materiały](../../MATERIALY.md#testing)

Wzorce organizacji dowodów i zastępników. **11 kart.**

**Status przykładów:** moduł dokumentacyjny. Fragmenty Java, SQL i HTTP oraz pseudokod są objaśnieniami
mechanizmu; kompletne modele i scenariusze poprawności również znajdują się w Markdown. Nie ma osobnej
aplikacji, buildu Java ani testów uruchamianych przez Maven.

**Rozbudowany kod i omówienie:** [historie błędów i niezmienników w Markdown](../../labs/README.md). Studium w
Markdown rozwija wskazany podzbiór wzorców tej kategorii.

**Jak rozumieć tę kategorię:** Test jest dowodem konkretnej własności w określonym modelu. Najpierw nazwij
błąd, który ma wykryć, potem dobierz zastępnik, dane i granicę wykonania. Test czystej domeny, test adaptera
bazy i eksperyment recovery uzupełniają się; żaden nie dowodzi automatycznie wszystkiego. Poniższe fragmenty
pokazują budowę dowodu. Pełne modele i asercje są zapisane w Markdown; moduł nie uruchamia ich automatycznie.

## Spis kart

- [Test Double / Dummy / Stub / Fake / Spy / Mock](#test-double--dummy--stub--fake--spy--mock)
- [Test Data Builder / Object Mother](#test-data-builder--object-mother)
- [Arrange–Act–Assert / Given–When–Then](#arrangeactassert--givenwhenthen)
- [Consumer-driven Contract / Provider Verification](#consumer-driven-contract--provider-verification)
- [Component Test / Hexagonal Test Harness](#component-test--hexagonal-test-harness)
- [Property-based Testing / Metamorphic Testing](#property-based-testing--metamorphic-testing)
- [Model-based Testing / State Machine Testing](#model-based-testing--state-machine-testing)
- [Deterministic Simulation / Fault Injection](#deterministic-simulation--fault-injection)
-
  [Golden Master / Approval Testing / Characterization Test](#golden-master--approval-testing--characterization-test)
- [Architecture Fitness Function / Dependency Rule Test](#architecture-fitness-function--dependency-rule-test)
- [Chaos Experiment / Game Day / Recovery Drill](#chaos-experiment--game-day--recovery-drill)

## Test Double / Dummy / Stub / Fake / Spy / Mock

**Intuicja:** Zależność testowa może jedynie zajmować miejsce, zwracać ustaloną odpowiedź, działać jako
uproszczona implementacja albo rejestrować wywołania. Test Double jest wspólną nazwą tych ról, a nie synonimem
mocka. Dobór wynika z tego, czy dowodem ma być wynik, stan czy konkretna interakcja na granicy.

**Problem i mechanizm:** Zastępnik ma określoną rolę: dane, odpowiedź, działającą uproszczoną implementację,
zapis interakcji albo weryfikację oczekiwań.

**Role i współpraca:** Dummy nie uczestniczy w zachowaniu, Stub dostarcza odpowiedź, Fake realizuje
uproszczony kontrakt, Spy zapisuje obserwacje, Mock sprawdza oczekiwania interakcji.

**Przykład:** Lista saved::add w teście OrderService jest prostym obserwowalnym adapterem; fake repo działa w
pamięci.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
var saved = new ArrayList<Order>();
Orders spy = saved::add;
PlaceOrder service = new PlaceOrder(spy);
service.execute(order);
assertEquals(List.of(order), saved);
```

**Konkretny przypadek użycia:** Sprawdzenie powiadomienia po zakupie

- **Wejście:** Use case potrzebuje zegara, płatności i odbiorcy email.
- **Przebieg:** Stub ustala wynik płatności, fake przechowuje stan, spy rejestruje wysłane wiadomości.
- **Oczekiwany wynik:** Scenariusz sprawdza jedną wiadomość po potwierdzeniu płatności i żadną po odmowie.
- **Błąd lub granica:** Mockowanie każdej metody wewnętrznej wiąże test ze strukturą kodu zamiast kontraktem.

**Kiedy zastosować:** Gdy prawdziwa zależność utrudnia kontrolę testu. Fake bazy nie dowodzi izolacji SQL,
nawet jeśli działa poprawnie w pamięci.

**Koszt i pułapka:** Fake nie dowodzi ograniczeń SQL i transakcji; mockowanie każdej metody wiąże test ze
strukturą kodu.

**Rozwinięcie i ślad wykonania:** Zastępnik wybiera się według pytania testu, nie nawyku użycia mock
frameworka. Fake repo pomaga sprawdzić regułę, spy zliczyć obserwowane wywołania, a test prawdziwej bazy
zweryfikować izolację. Dwa testy mogą korzystać z tego samego API, lecz dowodzić zupełnie innych własności.

**Ćwiczenie sprawdzające:** Wskaż, jaki błąd wykryje test z fake, a jaki dopiero test bazy.

## Test Data Builder / Object Mother

**Intuicja:** Przy złożonym obiekcie test często ukrywa ważny warunek w wielu nieistotnych argumentach
konstruktora. Test Data Builder daje poprawny punkt wyjścia i jawnie zmienia istotną cechę. Object Mother
dostarcza nazwane zestawy fixture, ale ich mutowalne współdzielenie może sprzęgać pozornie niezależne testy.

**Problem i mechanizm:** Dane testowe mają czytelne poprawne wartości domyślne i jawnie zmieniane cechy
istotne dla scenariusza.

**Role i współpraca:** Fabryka tworzy nowe dane, test wybiera wariant, domena normalnie waliduje wynik. Nazwy
should nie powinny zastępować czytelnych danych scenariusza.

**Przykład:** anOrder().withCurrency(PLN).withLine(2,100).build() pokazuje, które dane wpływają na regułę.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
Order order = anOrder()
        .withCurrency("PLN")
        .withLine("book", 2, 1500)
        .build();
// Każde build wydaje niezależne dane testu.
```

**Konkretny przypadek użycia:** Zamówienie z jedną istotną różnicą

- **Wejście:** Dwa scenariusze wymagają zwykłego zamówienia oraz zamówienia z wygasłą rezerwacją.
- **Przebieg:** Builder dostarcza jawne poprawne domyślne dane, scenariusz zmienia tylko termin.
- **Oczekiwany wynik:** Czytelnik widzi, że o wyniku decyduje wygasła rezerwacja.
- **Błąd lub granica:** Współdzielony mutowalny obiekt z Object Mother może przenosić zmiany między testami.

**Kiedy zastosować:** Gdy przygotowanie obiektów przesłania regułę. Nie ukrywaj w domyślnych wartościach
właśnie tego warunku, który test ma wyjaśnić.

**Koszt i pułapka:** Zbyt rozbudowana fabryka ukrywa ważne założenia; współdzielony mutowalny obiekt z Object
Mother zanieczyszcza testy.

**Rozwinięcie i ślad wykonania:** Domyślna fixture powinna być poprawna i mała, a test jawnie zmieniać cechę
decydującą o wyniku. Zbudowanie dwóch zamówień nie powinno dawać wspólnej mutowalnej listy pozycji. Test z
kontrprzykładem współdzielenia pomaga wykryć błąd samej fabryki danych.

**Ćwiczenie sprawdzające:** Zmień jeden obiekt fixture i sprawdź niezależność drugiego testu.

## Arrange–Act–Assert / Given–When–Then

**Intuicja:** Czytelnik testu powinien łatwo rozpoznać warunek początkowy, działanie i oczekiwany skutek.
Arrange–Act–Assert oraz Given–When–Then porządkują tę opowieść. Nie wymagają rytualnej liczby komentarzy, lecz
pomagają oddzielić przygotowanie od obserwacji, szczególnie w dłuższej historii awarii.

**Problem i mechanizm:** Test wyraźnie oddziela przygotowanie warunków, jedno zachowanie i obserwowalne
skutki.

**Role i współpraca:** Arrange ustala dane, Act wywołuje kontrakt, Assert sprawdza wynik i stan. Przy
odrzuceniu trzeba często dowieść także braku częściowej zmiany.

**Przykład:** Given wersja 0, When pierwszy zapis zwiększa wersję, Then drugi zapis z wersją 0 jest odrzucony.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
Order order = newDraftOrder(); // Given
order.pay();                  // When
assertEquals(PAID, order.status()); // Then
assertThrows(AlreadyPaid.class, order::pay);
assertEquals(PAID, order.status());
```

**Konkretny przypadek użycia:** Odrzucenie wypłaty ponad saldo

- **Wejście:** Saldo wynosi 100, żądanie wypłaty 101.
- **Przebieg:** Arrange ustala stan, Act wykonuje jedną operację, Assert sprawdza odmowę i saldo.
- **Oczekiwany wynik:** Saldo pozostaje 100, a komunikat wskazuje brak środków.
- **Błąd lub granica:** Samo sprawdzenie wyjątku nie dowodzi, że stan nie został wcześniej uszkodzony.

**Kiedy zastosować:** Dla testów zachowania z wyraźnym celem. Wiele niezwiązanych akcji w jednym teście może
utrudnić ustalenie, która własność zawiodła.

**Koszt i pułapka:** Podział na komentarze nie naprawia testu sprawdzającego prywatne pola zamiast kontraktu.

**Rozwinięcie i ślad wykonania:** Przygotuj konkretną wersję i warunek, wykonaj jedną decyzję, potem sprawdź
stan publiczny także po błędzie. Odrzucenie drugiej płatności nie wystarczy: test powinien dowieść, że
pierwszy legalny stan pozostał zachowany. Komentarze etapów są użyteczne przy dłuższej historii awarii.

**Ćwiczenie sprawdzające:** Zapisz kontrprzykład lost update jako test publicznego API.

## Consumer-driven Contract / Provider Verification

**Intuicja:** Zmiana providera może przejść jego własne testy, a mimo to zepsuć klienta potrzebującego
konkretnego pola. Consumer-driven Contract opisuje te potrzeby i pozwala zweryfikować je po stronie providera.
Chroni rzeczywiste użycie kontraktu, nie tylko zgodność obu stron z dowolną ogólną specyfikacją.

**Problem i mechanizm:** Oczekiwania konsumenta są wykonywane przeciw zgodnej wersji providera i publikowanemu
kontraktowi.

**Role i współpraca:** Konsument publikuje oczekiwania, provider je wykonuje na zgodnej implementacji,
pipeline wykrywa łamiącą zmianę. Dane i stany testowe muszą być jawne.

**Przykład:** Orders wymaga sku oraz available; provider Inventory weryfikuje format, statusy i wariant braku
towaru.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
contract.given("sku exists")
        .whenGet("/stock/book")
        .expectStatus(200)
        .expectField("sku", "book")
        .expectIntegerField("available");
```

**Konkretny przypadek użycia:** Zmiana odpowiedzi API przewoźnika

- **Wejście:** Konsument potrzebuje pól trackingId i status, dostawca chce usunąć status.
- **Przebieg:** Konsument publikuje kontrakt oczekiwań, provider uruchamia jego weryfikację na swojej
  implementacji.
- **Oczekiwany wynik:** Niezgodna zmiana jest wykryta przed wydaniem zależnej wersji.
- **Błąd lub granica:** Kontrakt odpowiedzi nie dowodzi działania całej sieci ani kompletności reguł
  biznesowych.

**Kiedy zastosować:** Dla niezależnie rozwijanych integracji. Kontrakt nie zastępuje testów pełnej sieci,
uwierzytelnienia i semantyki wieloetapowego procesu.

**Koszt i pułapka:** Zgodność kontraktu nie dowodzi pełnej semantyki biznesowej ani działania całej sieci.

**Rozwinięcie i ślad wykonania:** Provider wykonuje oczekiwania dla wspieranych konsumentów na własnej
aktualnej implementacji. Zgodny JSON bez pola dostępności jest nadal niezgodnym kontraktem dla klienta, który
potrzebuje tego pola do decyzji. Test nie zastępuje integracji sieciowej, uwierzytelniania i limitów czasu.

**Ćwiczenie sprawdzające:** Usuń wymagane pole i sprawdź, czy weryfikacja blokuje niezgodną zmianę.

## Component Test / Hexagonal Test Harness

**Intuicja:** Cały use case można sprawdzić bez uruchamiania każdej usługi zewnętrznej, jeśli ma jawne porty.
Component Test uruchamia prawdziwy komponent we wskazanej granicy, a zależności poza nią kontroluje. To
większy zakres niż pojedyncza metoda, ale nadal mniejszy niż cały produkt.

**Problem i mechanizm:** Use case uruchamia się z realnym rdzeniem i kontrolowanymi adapterami na granicach.

**Role i współpraca:** Realna aplikacja i domena są wewnątrz, fake porty na granicy, test steruje wejściem
oraz obserwuje wyniki. Zakres „component” trzeba nazwać, bo nie ma jednej uniwersalnej wielkości.

**Przykład:** OrderService zapisuje do fake portu, a zegar testowy przesuwa breaker bez Thread.sleep.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
Orders orders = new MemoryOrders();
Clock clock = Clock.fixed(instant, ZoneOffset.UTC);
ConfirmOrder useCase = new ConfirmOrder(orders, clock);
useCase.execute(command);
assertEquals(CONFIRMED, orders.get(orderId).status());
```

**Konkretny przypadek użycia:** Test komponentu rezerwacji

- **Wejście:** HTTP i prawdziwa baza otaczają moduł, zewnętrzna płatność jest kontrolowanym portem.
- **Przebieg:** Harness uruchamia granice komponentu i podaje odmowę płatności przez adapter testowy.
- **Oczekiwany wynik:** Można sprawdzić odpowiedź HTTP oraz zapis kompensacji w tej samej wersji komponentu.
- **Błąd lub granica:** Fake płatności nie dowodzi zgodności jej protokołu; potrzebny jest osobny kontrakt
  adaptera.

**Kiedy zastosować:** Gdy chcesz dowieść współpracy reguł i orkiestracji. Osobno testuj adapter, którego
prawdziwy protokół został tu zastąpiony.

**Koszt i pułapka:** Brak prawdziwego transportu ogranicza dowód; test integracyjny nadal potrzebny dla
gwarancji adaptera.

**Rozwinięcie i ślad wykonania:** Granica testowanego komponentu powinna być nazwana: realna domena i
application service, kontrolowane porty. DomainAndPersistenceLabTest sprawdza decyzję i zapis snapshotu, ale
nie JDBC. Oddziel test adaptera, aby błędu sterownika nie szukać w teście czystej reguły.

**Ćwiczenie sprawdzające:** Zidentyfikuj, które reguły są sprawdzone bez DB, a które wymagają DB.

## Property-based Testing / Metamorphic Testing

**Intuicja:** Kilka przykładów może nie ujawnić błędu dla innego rozkładu danych. Property-based Testing
sprawdza własność na wielu wejściach, a Metamorphic Testing relację między wynikami po znanej zmianie wejścia.
Wartość testu zależy od jakości własności i generatora, nie samej liczby losowań.

**Problem i mechanizm:** Zamiast samych przykładów sprawdza się niezmienniki wielu danych lub relacje między
przekształconymi wejściami.

**Role i współpraca:** Generator tworzy dane, właściwość opisuje niezmiennik, shrinking pomaga uprościć
kontrprzykład. Relacje dla merge można sprawdzać bez ręcznego wyliczania każdej sumy.

**Przykład:** Dla CRDT merge(a,b)=merge(b,a), a dla kopii dokumentu zmiana kopii nie zmienia oryginału.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
for (Counter a : examples) {
    for (Counter b : examples) {
        assertEquals(a.merge(b), b.merge(a));
        assertEquals(a, a.merge(a));
    }
}
```

**Konkretny przypadek użycia:** Własności sortowania zamówień

- **Wejście:** Generator tworzy listy z duplikatami, pustą listę i różne kolejności wejścia.
- **Przebieg:** Weryfikacja sprawdza porządek, zachowanie multizbioru i idempotencję ponownego sortowania.
- **Oczekiwany wynik:** Przeciwprzykład jest zmniejszony do danych ujawniających błąd.
- **Błąd lub granica:** Własność typu wynik nie jest null jest zbyt słaba; generator bez duplikatów ominie
  ważny przypadek.

**Kiedy zastosować:** Dla algorytmów z mocnymi prawami i dużą przestrzenią wejścia. Błędna własność może
utrwalać ten sam błąd co implementacja.

**Koszt i pułapka:** Właściwość może być zbyt słaba lub błędna; generator i shrinking decydują o jakości
kontrprzykładów.

**Rozwinięcie i ślad wykonania:** DistributedDataLabTest sprawdza merge CRDT na wielu parach wartości oraz
łączność z trzecim stanem. Oddzielny kontrprzykład pokazuje, że dwa niezależne zapisy tej samej składowej nie
sumują się. Sama matematyczna własność nie dowodzi poprawnego kontraktu własności replicaId.

**Ćwiczenie sprawdzające:** Wymyśl błąd, który przejdzie test jednej wartości, ale złamie przemienność merge.

## Model-based Testing / State Machine Testing

**Intuicja:** Błąd często wynika nie z pojedynczej operacji, lecz z kolejności wcześniejszych działań.
Model-based Testing porównuje implementację z prostym modelem, a State Machine Testing generuje legalne i
zabronione przejścia. Sprawdzanie po każdym kroku wskazuje pierwszy moment rozjazdu.

**Problem i mechanizm:** Historia operacji jest porównywana z prostym modelem dopuszczalnych stanów i przejść.

**Role i współpraca:** Model opisuje dozwolony stan, generator wybiera komendy, system realizuje publiczne
API, porównanie ocenia wyniki. Model powinien być prostszy i niezależny.

**Przykład:** Model Order dopuszcza NEW→PAID i odrzuca PAID→PAID; generator tworzy sekwencje poleceń.

**Mechanizm w kodzie:** Pseudokod: nazwy oznaczają role i operacje z opisu. To schemat algorytmu lub
interakcji, bez deklaracji implementacji infrastruktury.

```text
model: NEW --pay--> PAID
historia: create, pay, pay
po create: stan NEW
po pierwszym pay: PAID
po drugim pay: błąd i nadal PAID
```

**Konkretny przypadek użycia:** Model historii edytora

- **Wejście:** Model opisuje insert, undo i redo; generator tworzy sekwencje operacji.
- **Przebieg:** Po każdym kroku porównuje widoczną treść i dostępność operacji z prostym modelem.
- **Oczekiwany wynik:** Sekwencja insert, undo, insert wykrywa niepoprawne zachowanie starej gałęzi redo.
- **Błąd lub granica:** Model kopiujący implementację może powtórzyć ten sam błąd zamiast dać niezależny punkt
  odniesienia.

**Kiedy zastosować:** Dla lifecycle, historii i struktur ze złożonymi sekwencjami. Nie kopiuj całego kodu
implementacji do modelu oczekiwanego wyniku.

**Koszt i pułapka:** Model współdzielący kod z implementacją może powielić ten sam błąd; model powinien być
prostszy i niezależny.

**Rozwinięcie i ślad wykonania:** Model powinien mieć prostą tabelę legalnych przejść oraz niezależny sposób
obliczenia oczekiwanego stanu. Generowana historia może zawierać legalne i zabronione komendy. Sprawdzaj stan
po każdym kroku, aby znaleźć pierwszy moment rozjazdu, zamiast tylko końcowego salda.

**Ćwiczenie sprawdzające:** Wygeneruj legalne i nielegalne przejścia oraz porównaj stan po błędzie.

## Deterministic Simulation / Fault Injection

**Intuicja:** Test z sleep często liczy, że awaria nastąpi w odpowiednim momencie. Deterministyczna symulacja
daje kontrolę nad czasem i przeplotem, a Fault Injection nad konkretnym miejscem błędu. Dzięki temu historię
można powtórzyć i wyjaśnić, zamiast przypisywać wynik losowej prędkości maszyny.

**Problem i mechanizm:** Kontrolowane czas, scheduler i punkty awarii odtwarzają powtarzalne niekorzystne
historie.

**Role i współpraca:** Zegar testowy steruje czasem, hook awarii granicą operacji, obserwacja sprawdza
niezmiennik po wznowieniu. Model obejmuje tylko jawnie zasymulowane zachowania.

**Przykład:** Test Outbox przerywa dokładnie po publish przed ack, a breaker używa AtomicLong jako zegara.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
outbox.place("o1", noFailure);
assertThrows(Crash.class, () -> outbox.relay(broker::add, crashAfterPublish));
outbox.relay(broker::add, noFailure);
assertEquals(2, broker.size());
// Następny etap sprawdza jeden efekt po deduplikacji.
```

**Konkretny przypadek użycia:** Symulacja dostarczania wiadomości

- **Wejście:** Ziarno generatora ustala duplikaty, opóźnienia i restart konsumenta.
- **Przebieg:** Wirtualny zegar i scheduler odtwarzają konkretną kolejność bez realnego sleep.
- **Oczekiwany wynik:** Ten sam seed odtwarza awarię, a asercje sprawdzają niezmienniki historii.
- **Błąd lub granica:** Symulator sprawdza tylko modelowane awarie; brak modelu utraty potwierdzenia ukrywa
  istotny przypadek.

**Kiedy zastosować:** Dla timeoutów, duplikatów, retry i okien commit–ack. Lokalny hook nie dowodzi zachowania
dysku podczas utraty zasilania.

**Koszt i pułapka:** Model nie symuluje całej sieci, dysku i pamięci; potrzebne są jawne granice
wiarygodności.

**Rozwinięcie i ślad wykonania:** Zegar AtomicLong oraz hook przed commitem pozwalają wskazać dokładne miejsce
awarii. Nie trzeba liczyć, że Thread.sleep trafi w wyścig. Determinizm dotyczy jednak modelowanej historii;
nie symuluje automatycznie fsync, pakietów TCP i zachowania silnika bazy podczas restartu.

**Ćwiczenie sprawdzające:** Odtwórz tę samą historię dwukrotnie bez sleep i sprawdź identyczny wynik.

## Golden Master / Approval Testing / Characterization Test

**Intuicja:** Przed refaktoryzacją starego kodu nie zawsze masz pełną wiedzę o jego zachowaniu.
Characterization Test utrwala obserwację, a Golden Master lub Approval Test porównuje większy wynik z
zaakceptowanym wzorcem. Pomaga wychwycić niezamierzoną zmianę, ale może również utrwalić istniejący błąd.

**Problem i mechanizm:** Utrwalone obserwowalne zachowanie umożliwia bezpieczną zmianę istniejącego kodu.

**Role i współpraca:** Zestaw reprezentatywnych danych zasila kod, normalizacja usuwa uzasadnioną
niedeterministyczność, reviewer ocenia różnicę. Aktualizacja wzorca jest decyzją, nie automatycznym krokiem
„naprawy testu”.

**Przykład:** Przed refaktoryzacją parsera zapisuje się zestaw wyników dla historycznych plików i porównuje po
zmianie.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
String actual = renderer.render(exampleDocument);
String normalized = removeOnlyGeneratedTimestamp(actual);
approvals.verify(normalized);
// Zmiana wzorca wymaga oceny znaczenia różnicy.
```

**Konkretny przypadek użycia:** Refaktoryzacja starego generatora faktur

- **Wejście:** Zestaw wejść ma ustalone odpowiedzi, w tym pola czasu i losowe identyfikatory.
- **Przebieg:** Test normalizuje świadomie niestabilne pola, porównuje wynik i pokazuje różnice do oceny.
- **Oczekiwany wynik:** Zmiana układu lub kwoty jest widoczna przed zaakceptowaniem nowego wzorca.
- **Błąd lub granica:** Automatyczne zatwierdzenie całego diff utrwala błędy; stara odpowiedź może już
  zawierać wadę biznesową.

**Kiedy zastosować:** Dla bezpiecznego poznawania legacy i rozbudowanych wyników. Zachowaj osobne testy
poprawności znanych reguł, zamiast uznawać każdy historyczny wynik za prawidłowy.

**Koszt i pułapka:** Snapshot może utrwalić błąd lub przypadkowy format; aktualizacja oczekiwań wymaga
merytorycznego review.

**Rozwinięcie i ślad wykonania:** Wynik golden master powinien pomijać tylko uzasadnione elementy
niedeterministyczne, a nie wszystko, co często się zmienia. Aktualizacja snapshotu jest decyzją o
zaakceptowaniu zachowania. Gdy istniejąca implementacja ma znany błąd, zachowaj osobny test oczekiwanej
poprawki zamiast utrwalać go bez komentarza.

**Ćwiczenie sprawdzające:** Oddziel pola deterministyczne od czasu i losowych ID przed porównaniem.

## Architecture Fitness Function / Dependency Rule Test

**Intuicja:** Granice pakietów łatwo naruszyć jednym wygodnym importem. Architecture Test zamienia ważną
regułę zależności w automatycznie sprawdzany warunek. Fitness Function obserwuje wybraną cechę projektu w
czasie, aby nie polegać wyłącznie na pamięci reviewera.

**Problem i mechanizm:** Automatyczna reguła pilnuje istotnej granicy projektu podczas kolejnych zmian.

**Role i współpraca:** Reguła nazywa dozwolone zależności, skaner odczytuje kod lub bytecode, build blokuje
naruszenie. Test nie ocenia całej jakości modelu biznesowego.

**Przykład:** Build odrzuca import Springa w domain i dostęp Orders do Inventory.internal.

**Mechanizm w kodzie:** Pseudokod: nazwy oznaczają role i operacje z opisu. To schemat algorytmu lub
interakcji, bez deklaracji implementacji infrastruktury.

```text
reguła: domain nie zależy od Spring ani JDBC
reguła: Orders nie importuje Inventory.internal
legalne: Orders -> InventoryApi
nielegalne: Orders -> Inventory.internal.StockEntity
```

**Konkretny przypadek użycia:** Ochrona granic modułu zamówień

- **Wejście:** Kod domeny nie może zależeć od pakietów kontrolerów ani SQL.
- **Przebieg:** Reguła architektoniczna analizuje zależności i zgłasza konkretną niedozwoloną krawędź.
- **Oczekiwany wynik:** Nowy import adaptera w domenie przerywa sprawdzenie architektury.
- **Błąd lub granica:** Brak statycznej zależności nie dowodzi izolacji danych; współdzielenie tabel wymaga
  dodatkowej kontroli.

**Kiedy zastosować:** Dla granic, które mają długotrwałą wartość. Wąskie, uzasadnione wyjątki są czytelniejsze
niż stopniowe wyłączanie całej reguły.

**Koszt i pułapka:** Test pakietów nie dowodzi dobrego modelu; zbyt ogólna reguła może zabronić uzasadnionego
wyjątku.

**Rozwinięcie i ślad wykonania:** Reguła importów chroni kierunek zależności, ale publiczny interfejs może
nadal ujawniać techniczne DTO i semantykę tabel. Przegląd musi więc ocenić również kontrakt. Wyjątki od reguły
powinny być wąskie i nazwane, aby nie zamieniły testu w pustą dekorację builda.

**Ćwiczenie sprawdzające:** Zapisz jedno legalne i jedno nielegalne połączenie modułów.

## Chaos Experiment / Game Day / Recovery Drill

**Intuicja:** Niektórych własności recovery nie dowiedzie sama metoda jednostkowa. Chaos Experiment w
kontrolowanym zakresie wprowadza awarię i porównuje wynik z hipotezą, a Game Day ćwiczy również reakcję ludzi
i procedur. Musi mieć zakres wpływu, kryterium sukcesu i warunek przerwania.

**Problem i mechanizm:** Kontrolowana awaria ma hipotezę, ograniczony zakres, obserwację i warunki przerwania.

**Role i współpraca:** Eksperyment wybiera awarię, obserwacje mierzą proces, runbook opisuje działanie,
ograniczenia zapobiegają niekontrolowanemu rozszerzeniu skutków.

**Przykład:** W środowisku ćwiczeniowym wyłącza się broker, a po powrocie sprawdza backlog i brak podwójnych
skutków.

**Mechanizm w kodzie:** Pseudokod: nazwy oznaczają role i operacje z opisu. To schemat algorytmu lub
interakcji, bez deklaracji implementacji infrastruktury.

```text
hipoteza: po powrocie brokera backlog znika bez podwójnych wysyłek
awaria: broker niedostępny przez określony czas w środowisku ćwiczeń
obserwacje: outbox age, liczba dostaw, unikalne skutki
przerwij: przekroczony uzgodniony zasięg lub czas
```

**Konkretny przypadek użycia:** Ćwiczenie utraty repliki

- **Wejście:** System ma dwie repliki i ustalone granice błędów oraz czasu odtworzenia.
- **Przebieg:** Kontrolowany eksperyment zatrzymuje jedną replikę, obserwuje ruch i przerywa po przekroczeniu
  granic.
- **Oczekiwany wynik:** Zespół poznaje rzeczywisty czas przełączenia i skuteczność procedury naprawy.
- **Błąd lub granica:** Eksperyment bez warunku przerwania i obserwowalności nie daje kontrolowanego dowodu
  odporności.

**Kiedy zastosować:** Dla weryfikacji konkretnych założeń niezawodności w odpowiednim środowisku. Wyłączenie
procesu bez hipotezy i oceny danych jest tylko zdarzeniem, nie pełnym dowodem.

**Koszt i pułapka:** To praktyka eksperymentalna, nie automatycznie test produkcyjny; środowisko i
dopuszczalny wpływ muszą być jawne.

**Rozwinięcie i ślad wykonania:** Eksperyment ma mierzalną hipotezę, np. backlog zostaje opróżniony bez
podwójnych skutków po odzyskaniu brokera. Określ zakres, limit czasu i warunek przerwania przed awarią. Samo
wyłączenie procesu i obserwacja zielonego dashboardu nie dowodzi zachowania niezmiennika biznesowego.

**Ćwiczenie sprawdzające:** Zdefiniuj mierzalną hipotezę recovery oraz warunek zakończenia eksperymentu.
