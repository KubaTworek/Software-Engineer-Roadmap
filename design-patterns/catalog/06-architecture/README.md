# Architektura aplikacji i usług

[Moduł](../../README.md) · [Indeks](../../INDEKS.md) · [Materiały](../../MATERIALY.md#architecture)

Style architektoniczne i wzorce granic. **18 kart.**

**Status przykładów:** moduł dokumentacyjny. Fragmenty Java, SQL i HTTP oraz pseudokod są objaśnieniami
mechanizmu; kompletne modele i scenariusze poprawności również znajdują się w Markdown. Nie ma osobnej
aplikacji, buildu Java ani testów uruchamianych przez Maven.

**Rozbudowany kod i omówienie:**
[przypadek użycia z portem i adapterem](../../labs/01-domain-and-persistence.md). Studium w Markdown rozwija
wskazany podzbiór wzorców tej kategorii.

**Jak rozumieć tę kategorię:** Architektura ustala granice odpowiedzialności i zależności większe niż
pojedyncza metoda. Oceniaj osobno organizację kodu, jednostkę wdrożenia, własność danych oraz sposób
komunikacji. Hexagonal może działać w modularnym monolicie, mikroserwisy mogą używać eventów i API
jednocześnie, a Gateway nie zastępuje autoryzacji w usługach. Fragmenty pokazują te granice w uproszczeniu;
nie uruchamiają opisanej topologii.

## Spis kart

- [Layered Architecture / N-tier](#layered-architecture--n-tier)
- [Hexagonal Architecture / Ports and Adapters](#hexagonal-architecture--ports-and-adapters)
- [Clean Architecture / Onion Architecture](#clean-architecture--onion-architecture)
- [Modular Monolith / Package by Feature](#modular-monolith--package-by-feature)
- [Microservices / Database per Service](#microservices--database-per-service)
- [Event-driven Architecture](#event-driven-architecture)
- [Microkernel / Plugin Architecture](#microkernel--plugin-architecture)
- [Pipes and Filters](#pipes-and-filters)
- [Model–View–Controller / Front Controller](#modelviewcontroller--front-controller)
- [MVP / MVVM](#mvp--mvvm)
- [API Gateway / Gateway Routing](#api-gateway--gateway-routing)
- [Backend for Frontend](#backend-for-frontend)
- [API Composition / Gateway Aggregation](#api-composition--gateway-aggregation)
- [Strangler Fig / Branch by Abstraction](#strangler-fig--branch-by-abstraction)
- [Sidecar / Ambassador](#sidecar--ambassador)
- [Service Mesh](#service-mesh)
- [Service Discovery / Service Registry](#service-discovery--service-registry)
- [Cell-based Architecture / Deployment Stamps](#cell-based-architecture--deployment-stamps)

## Layered Architecture / N-tier

**Intuicja:** W nieuporządkowanym kodzie endpoint może jednocześnie liczyć cenę i składać SQL. Warstwy grupują
odpowiedzialności według roli, aby zmiana transportu nie wymuszała przepisywania reguł. N-tier opisuje także
fizyczne rozmieszczenie, więc logiczna warstwa nie musi oznaczać osobnego serwera.

**Problem i mechanizm:** Odpowiedzialności dzieli się na warstwy ze świadomym kierunkiem zależności.

**Role i współpraca:** Controller tłumaczy wejście, service koordynuje przypadek użycia, model pilnuje reguł,
adapter realizuje dostęp do danych. Zależności powinny przechodzić przez uzgodnione granice.

**Przykład:** HTTP controller wywołuje application service, ten model i port persystencji; infrastruktura
obsługuje bazę.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
// Warstwa wejściowa:
OrderResponse post(OrderRequest input) {
    OrderResult result = placeOrder.execute(input.toCommand());
    return OrderResponse.from(result);
}
// SQL i reguły ceny pozostają poza kontrolerem.
```

**Konkretny przypadek użycia:** Endpoint potwierdzenia

- **Wejście:** HTTP podaje order42, domena musi sprawdzić stan i zapisać wynik.
- **Przebieg:** Controller mapuje komendę, application service koordynuje, model waliduje, adapter zapisuje.
- **Oczekiwany wynik:** Reguła ceny nie zależy od formatu odpowiedzi HTTP.
- **Błąd lub granica:** Puste warstwy przekazujące wszystko bez własnego kontraktu dodają koszt bez realnej
  granicy.

**Kiedy zastosować:** Gdy rozdział odpowiedzialności ułatwia zmianę i testowanie. Unikaj pustych warstw, które
tylko przekazują identyczny argument bez własnego kontraktu.

**Koszt i pułapka:** Logiczne warstwy nie wymagają osobnych hostów. Omijanie warstw i anemiczne delegowanie
tworzą pozorną architekturę.

**Rozwinięcie i ślad wykonania:** Prześledź wywołanie od HTTP do zapisu i kierunek importów w odwrotną stronę.
Warstwa logiczna może działać w tym samym procesie co pozostałe. Istotne jest, czy domena zna format
transportu i sterownik bazy, a nie liczba katalogów o nazwach controller, service oraz repository.

**Ćwiczenie sprawdzające:** Prześledź PlaceOrder i wskaż, w której warstwie znajduje się niezmiennik.

## Hexagonal Architecture / Ports and Adapters

**Intuicja:** Reguła potwierdzania zamówienia nie powinna zależeć od tego, czy uruchomił ją HTTP, test czy
odczyt kolejki. Ports and Adapters ustawia tę regułę w środku, a technologie na zewnątrz. Port opisuje
potrzebę aplikacji; adapter tłumaczy ją na konkretne urządzenie lub protokół.

**Problem i mechanizm:** Rdzeń komunikuje się przez porty; adaptery wejściowe i wyjściowe tłumaczą
technologię.

**Role i współpraca:** Use case wywołuje Orders, a MemoryOrders albo SqlOrders implementuje ten port. Adapter
wejściowy tworzy komendę niezależnie od adaptera wyjściowego.

**Przykład:** OrderService zależy od OrderRepository; test daje adapter w pamięci, produkcja adapter SQL.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
interface Orders { void save(Order order); }
record PlaceOrder(Orders orders) {
    void execute(Order order) {
        order.validate();
        orders.save(order);
    }
}
```

**Konkretny przypadek użycia:** Ten sam use case trzema wejściami

- **Wejście:** Potwierdzenie uruchamia REST, konsument eventu i scenariusz dokumentacyjny.
- **Przebieg:** Wszystkie wejścia wywołują ten sam port aplikacji; adapter wyjściowy realizuje Orders.
- **Oczekiwany wynik:** Zmiana JDBC na inne źródło nie wymaga zmiany reguły confirm.
- **Błąd lub granica:** Interfejs z typem EntityManager nadal przecieka technologię do rdzenia.

**Kiedy zastosować:** Gdy chcesz uruchamiać rdzeń bez infrastruktury. Nie twórz interfejsu dla każdego
pomocnika; port ma wyrażać rzeczywistą granicę aplikacji.

**Koszt i pułapka:** Interfejs dla każdej klasy nie tworzy granicy. Port powinien wynikać z potrzeby rdzenia.

**Rozwinięcie i ślad wykonania:** Port Orders opisuje potrzebę przypadku użycia, a MemoryOrders spełnia ją w
teście. Adapter SQL mógłby zostać podany w composition root bez zmiany confirm. Wzorzec izoluje politykę od
urządzenia, ale kontrakt portu musi nadal określać konflikt wersji i moment uznania zapisu za udany.

**Ćwiczenie sprawdzające:** Uruchom use case bez HTTP i bazy, nie zmieniając jego kodu.

## Clean Architecture / Onion Architecture

**Intuicja:** To, co zmienia się najczęściej technicznie, nie powinno narzucać formy najważniejszym regułom.
Clean i Onion kierują zależności kompilacji do środka: infrastruktura zna polityki, a polityki nie importują
sterownika, controllera ani ORM. Ich diagramy warstw są pokrewne, lecz nie stanowią jednego sztywnego
przepisu.

**Problem i mechanizm:** Zależności kompilacji kieruje się do polityk domenowych; use case nie importuje
frameworka infrastruktury.

**Role i współpraca:** Domena i use case definiują potrzebne kontrakty. Adapter zewnętrzny je implementuje, a
composition root łączy obiekty. DTO granicy nie powinno wymagać obcych typów persystencji.

**Przykład:** Pakiet domain nie zna Springa; adapter JPA mapuje własną encję na model domenowy, a konfiguracja
je łączy.

**Mechanizm w kodzie:** Pseudokod: nazwy oznaczają role i operacje z opisu. To schemat algorytmu lub
interakcji, bez deklaracji implementacji infrastruktury.

```text
domain: Order, Money
application: ConfirmOrder, port Orders
infrastructure: SqlOrders implements Orders
http: ConfirmOrderController

Zależności: http -> application -> domain
              infrastructure -> port application
```

**Konkretny przypadek użycia:** Niezależny rdzeń reguł

- **Wejście:** Zespół zmienia web framework, ale nie reguły rezerwacji.
- **Przebieg:** Porty są w application, adapter infrastruktury od nich zależy, domena nie importuje
  frameworka.
- **Oczekiwany wynik:** Rdzeń zachowuje własny model i kontrakty mimo wymiany transportu.
- **Błąd lub granica:** Pięć kopii prostego DTO może być nadmiernym kosztem, jeśli nie reprezentują realnych
  granic.

**Kiedy zastosować:** Gdy niezależność reguł od technologii ma wartość. Dla małego CRUD porównaj ten zysk z
kosztem dodatkowego mapowania.

**Koszt i pułapka:** Więcej mapowania może być niepotrzebne w małym CRUD. Onion i Clean są pokrewnymi stylami,
nie identyczną liczbą warstw.

**Rozwinięcie i ślad wykonania:** Najpierw wskaż politykę, która ma pozostać niezależna od szczegółów
wdrożenia. Potem umieść kontrakt po stronie jej klienta i odwróć zależność adaptera. Nie twórz pięciu modeli
tego samego prostego formularza, jeśli koszt mapowania przewyższa korzyść z planowanej izolacji.

**Ćwiczenie sprawdzające:** Wskaż import łamiący granicę i przenieś go do adaptera.

## Modular Monolith / Package by Feature

**Intuicja:** Jedno wdrożenie nie musi oznaczać jednej nieograniczonej przestrzeni zależności. Modular
Monolith dzieli aplikację według możliwości biznesowych i daje modułom publiczne wejścia. Dzięki temu Orders
może korzystać z Inventory, nie sięgając do jego prywatnych klas i tabel.

**Problem i mechanizm:** Jedno wdrożenie zachowuje jawne moduły biznesowe i ograniczone API między nimi.

**Role i współpraca:** Moduł jest właścicielem reguł oraz danych, API modułu kontraktem współpracy,
composition root punktem złożenia. Package by Feature pomaga widzieć te granice w kodzie.

**Przykład:** Orders wywołuje publiczny kontrakt Inventory, bez odczytu jego tabel i klas internal.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
// Publiczny kontrakt modułu Inventory:
interface InventoryApi {
    ReservationId reserve(Sku sku, int quantity);
}
// Orders korzysta z API, nie z Inventory.internal.StockEntity.
ReservationId hold = inventory.reserve(sku, quantity);
```

**Konkretny przypadek użycia:** Granice sklepu w jednym wdrożeniu

- **Wejście:** Orders i Inventory mają oddzielne pakiety internal i publiczne API.
- **Przebieg:** Orders korzysta z reserve przez InventoryApi.
- **Oczekiwany wynik:** Jedna aplikacja nadal ma jawnego właściciela zapasu.
- **Błąd lub granica:** Bezpośredni dostęp Orders do tabel Inventory łamie granicę mimo poprawnej nazwy
  modułu.

**Kiedy zastosować:** Gdy chcesz silnych granic bez kosztu sieci i wielu wdrożeń. Sam katalog z nazwą modułu
nie ochroni ich bez reguł dostępu i kontroli zależności.

**Koszt i pułapka:** Wspólny proces nie wymusza granic; wspólne transakcje mogą zwiększać sprzężenie.

**Rozwinięcie i ślad wykonania:** Moduł Orders może wywoływać publiczny interfejs Inventory, lecz nie otwiera
jego tabel ani klas internal. W jednym wdrożeniu nadal można egzekwować to przez pakiety i testy zależności.
Wyznacz granice transakcji pomiędzy modułami, zanim wyniesiesz je do niezależnych procesów.

**Ćwiczenie sprawdzające:** Zapisz test architektury zabraniający importu cudzych pakietów internal.

## Microservices / Database per Service

**Intuicja:** Gdy fragmenty systemu wymagają niezależnego wdrażania i utrzymania, można wydzielić usługi z
własnością danych. Mikroserwis nie jest tylko klasą uruchomioną osobno: jego konsumenci muszą polegać na
kontrakcie, a nie prywatnym schemacie. Wspólna operacja biznesowa staje się wtedy procesem między niezależnymi
granicami.

**Problem i mechanizm:** Niezależnie wdrażane usługi posiadają swoje dane i publiczne kontrakty integracji.

**Role i współpraca:** Inventory posiada zapas, Orders zamówienia, a API i eventy przenoszą uzgodnione
informacje. Database per Service oznacza własność dostępu, nie koniecznie osobną fizyczną maszynę dla każdej
bazy.

**Przykład:** Inventory zapisuje stan we własnej bazie; Orders reaguje na eventy zamiast wykonywać JOIN w
cudzym schemacie.

**Mechanizm w kodzie:** Pseudokod: nazwy oznaczają role i operacje z opisu. To schemat algorytmu lub
interakcji, bez deklaracji implementacji infrastruktury.

```text
Orders DB <-- tylko Orders --> API / OrderPlaced
                                 |
Inventory DB <-- tylko Inventory --> ReservationResult

Orders nie wykonuje UPDATE w tabeli zapasu Inventory.
```

**Konkretny przypadek użycia:** Osobno wdrażany magazyn

- **Wejście:** Inventory zmienia swój schemat bez wspólnego release Orders.
- **Przebieg:** Usługi integrują się przez wersjonowane API i fakty, każda posiada swoje dane.
- **Oczekiwany wynik:** Zmiana tabel magazynu nie wymaga JOIN po stronie Orders.
- **Błąd lub granica:** Niezależne bazy oznaczają koszt procesu i spójności, nie darmowy rozproszony commit.

**Kiedy zastosować:** Gdy niezależność zmian uzasadnia koszty operacyjne. Jeśli każda zmiana wymaga wspólnego
release wszystkich usług, podział może nie dawać oczekiwanej autonomii.

**Koszt i pułapka:** Sieć, operacje i spójność kosztują więcej niż wywołanie lokalne. Wspólna baza osłabia
niezależność.

**Rozwinięcie i ślad wykonania:** Niezależność bazy oznacza brak bocznych zapisów do danych innej usługi.
Odczyt wielodomenowy wymaga API Composition albo projekcji, a proces biznesowy sagi lub innej koordynacji. Sam
podział na trzy JAR-y uruchamiane razem przy wspólnym schemacie nie daje niezależności zmian i rolloutów.

**Ćwiczenie sprawdzające:** Zaprojektuj odczyt zamówienia i zapasu bez dostępu do cudzej tabeli.

## Event-driven Architecture

**Intuicja:** Bezpośrednie wołanie wszystkich reakcji wymaga ich dostępności podczas głównego requestu.
Event-driven Architecture pozwala zatwierdzić lokalny fakt i przekazać go niezależnym odbiorcom. Nie usuwa
pracy: przenosi ją w czasie oraz wymaga jawnego postępu, powtórzeń i reguł odbudowy.

**Problem i mechanizm:** Komponenty reagują na fakty przekazywane asynchronicznie zamiast łańcucha
bezpośrednich wywołań.

**Role i współpraca:** Producent publikuje fakt, transport go przechowuje lub przekazuje, konsument podejmuje
własną decyzję. Każda subskrypcja ma własny stan przetwarzania.

**Przykład:** OrderPlaced uruchamia projekcję i wysyłkę; każdy odbiorca ma własny postęp i deduplikację.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
transaction.run(() -> {
    orders.save(confirmedOrder);
    outbox.append(new OrderPlaced(eventId, orderId));
});
// Niezależny odbiorca:
inbox.once(eventId, () -> shipping.schedule(orderId));
```

**Konkretny przypadek użycia:** Niezależna indeksacja

- **Wejście:** OrderPlaced ma zaktualizować search, które jest niedostępne 10 minut.
- **Przebieg:** Producent utrwala fakt, search po powrocie kontynuuje własny postęp.
- **Oczekiwany wynik:** Checkout nie czeka na indeks, a projekcję można odtworzyć.
- **Błąd lub granica:** Brak retencji lub deduplikacji może przerwać historię albo powielić skutek.

**Kiedy zastosować:** Gdy reakcje mogą być asynchroniczne i potrzebują niezależności. Nie wybieraj eventów,
jeśli klient potrzebuje natychmiastowej, silnie spójnej odpowiedzi całego procesu.

**Koszt i pułapka:** Opóźnienie, kolejność, duplikaty i ewolucja schematu stają się częścią kontraktu.

**Rozwinięcie i ślad wykonania:** Producent zatwierdza swój fakt bez czekania na wszystkie reakcje. Każdy
odbiorca ma własny postęp, a odczyt całości może być chwilowo niespójny. Narysuj duplikat, spóźniony event i
niedostępnego odbiorcę, żeby odkryć wymagania deduplikacji, kolejności i możliwości odbudowy.

**Ćwiczenie sprawdzające:** Narysuj moment commitu i sytuację, gdy jeden odbiorca działa godzinę później.

## Microkernel / Plugin Architecture

**Intuicja:** Edytor albo importer może mieć stabilny rdzeń i wiele rozszerzeń formatów. Microkernel skupia
podstawowe reguły w rdzeniu, a zmienne możliwości dostarcza jako pluginy przez określone SPI. Nowa wtyczka nie
powinna wymagać dopisywania warunku do każdej metody rdzenia.

**Problem i mechanizm:** Stabilny rdzeń deleguje rozszerzenia do wtyczek o ograniczonym kontrakcie.

**Role i współpraca:** Rdzeń zna ReaderProvider, plugin deklaruje obsługiwany format, mechanizm rejestracji
wybiera dostawcę. SPI określa również błędy i zgodność wersji.

**Przykład:** Importer ładuje ReaderProvider przez ServiceLoader, a pluginy dodają formaty bez modyfikacji
rdzenia.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
interface ReaderProvider {
    boolean supports(String format);
    Reader create();
}
ReaderProvider chosen = providers.stream()
        .filter(provider -> provider.supports(format))
        .findFirst().orElseThrow();
```

**Konkretny przypadek użycia:** Plugin nowego formatu

- **Wejście:** Importer obsługuje CSV i JSON, partner dodaje format XML.
- **Przebieg:** Nowy ReaderProvider implementuje SPI i rejestruje swoją możliwość.
- **Oczekiwany wynik:** Rdzeń importu wybiera plugin bez nowego warunku w każdym etapie.
- **Błąd lub granica:** ServiceLoader nie izoluje niezaufanego kodu ani konfliktujących bibliotek pluginów.

**Kiedy zastosować:** Gdy rozszerzenia mają własny cykl rozwoju. ServiceLoader nie izoluje niezaufanego kodu
ani nie rozwiązuje konfliktów zależności.

**Koszt i pułapka:** Wersje SPI, izolacja zależności i lifecycle pluginów wymagają kontraktu; ServiceLoader
nie jest sandboxem.

**Rozwinięcie i ślad wykonania:** Rdzeń zna SPI, a provider rejestruje obsługiwany format. Konfiguracja
wybiera implementację przed przetwarzaniem, więc reguły importu nie zawierają warunków dla każdego pluginu.
Wspólny classloader nie izoluje obcego kodu; nie traktuj rozszerzenia jako bezpiecznego miejsca wykonania
niezaufanej wtyczki.

**Ćwiczenie sprawdzające:** Dodaj wtyczkę CSV i opisz konflikt dwóch providerów tego samego formatu.

## Pipes and Filters

**Intuicja:** Import składa się z kilku różnych przekształceń. Pipes and Filters nadaje każdemu etapowi wąską
rolę i łączy je przepływem danych. Pozwala wymieniać lub dodawać etapy bez zmiany ich wnętrza, jeśli zachowane
są kontrakty wejścia, wyjścia i błędu.

**Problem i mechanizm:** Niezależne etapy przetwarzania łączy się przepływem danych przez ustalone kontrakty.

**Role i współpraca:** Filtr otrzymuje dane i wydaje wynik, pipe łączy etapy. Parser nie musi znać
zapisującego adaptera, a walidator nie zarządza całym źródłem wejścia.

**Przykład:** Wiersz CSV przechodzi parse → validate → normalize → persist; błędy trafiają do jawnego wyjścia.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
RawRow row = source.next();
ParsedRow parsed = parser.parse(row);
ValidatedRow checked = validator.validate(parsed);
NormalizedRow normalized = normalizer.apply(checked);
writer.persist(normalized);
```

**Konkretny przypadek użycia:** Pipeline importu ofert

- **Wejście:** Porcja ma 100 wierszy, 3 są semantycznie błędne.
- **Przebieg:** Parse, validate i normalize mają osobne kontrakty, polityka wybiera odrzucenie wiersza lub
  porcji.
- **Oczekiwany wynik:** Wynik jawnie opisuje 97 sukcesów i 3 odmowy albo rollback całości.
- **Błąd lub granica:** Późny błąd nie cofnie wcześniejszych niezależnych zapisów tylko przez kompozycję
  funkcji.

**Kiedy zastosować:** Gdy przetwarzanie ma stabilne granice etapów. I/O, transakcje i backpressure muszą mieć
dodatkowy kontrakt, nie wynikają z samej listy funkcji.

**Koszt i pułapka:** Granice transakcji i backpressure nie powstają od samej kompozycji funkcji.

**Rozwinięcie i ślad wykonania:** Każdy etap ma określone wejście, wyjście i kanał błędu. Walidator może
odrzucić jeden rekord albo całą porcję, co zmienia sposób agregowania wyniku. Jeśli etap wykonuje zapis,
późniejszy błąd nie wycofa go przez samą kompozycję funkcji; potrzebna jest transakcja lub kompensacja.

**Ćwiczenie sprawdzające:** Wstaw nowy filtr bez zmiany parsera i opisz obsługę częściowo błędnej paczki.

## Model–View–Controller / Front Controller

**Intuicja:** W interaktywnym systemie dane i reguły nie powinny być przywiązane do rysowania widoku. MVC
rozdziela model, prezentację i interpretowanie działań użytkownika. Front Controller centralizuje wejście oraz
routing, co jest powiązaną, ale odrębną odpowiedzialnością od logiki konkretnego ekranu.

**Problem i mechanizm:** Oddziela model, prezentację i interpretację interakcji; Front Controller centralizuje
wejście żądań.

**Role i współpraca:** Controller przyjmuje zamiar, model wykonuje reguły, view prezentuje przygotowane dane.
W aplikacji HTTP dispatcher może pełnić rolę Front Controllera.

**Przykład:** Dispatcher kieruje GET /orders do kontrolera, model widoku zawiera tylko pola ekranu.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
OrderView show(OrderId id) {
    OrderSummary summary = query.loadSummary(id);
    return new OrderView(summary.id(), summary.total(), summary.status());
}
// Warstwa widoku prezentuje OrderView, nie wykonuje reguł ceny.
```

**Konkretny przypadek użycia:** Widok zamówienia

- **Wejście:** GET order42 zwraca numer, status i sumę.
- **Przebieg:** Controller uruchamia query, model widoku zawiera tylko potrzebne pola.
- **Oczekiwany wynik:** Renderowanie nie wykonuje niejawnych reguł ani lazy SQL.
- **Błąd lub granica:** Udostępnienie encji bezpośrednio widokowi może ujawnić pole lub wykonać N+1 podczas
  serializacji.

**Kiedy zastosować:** Gdy oddzielenie prezentacji pomaga niezależnie rozwijać widoki. REST bez renderowanych
ekranów nie musi realizować całego klasycznego modelu MVC.

**Koszt i pułapka:** Kontroler nie powinien przejmować reguł domenowych. REST API bez widoków nie realizuje
automatycznie pełnego klasycznego MVC.

**Rozwinięcie i ślad wykonania:** Front Controller może jednolicie obsługiwać routing, błędy i kontekst
requestu. Konkretny kontroler tłumaczy wejście na komendę use case i wynik na model widoku. Wyświetlane pole
total nie powinno uruchamiać niejawnego, zdalnego wywołania przez getter encji w trakcie renderowania.

**Ćwiczenie sprawdzające:** Oddziel DTO odpowiedzi od encji bazy i wskaż wspólną obsługę błędów.

## MVP / MVVM

**Intuicja:** Widok bywa trudny do testowania, gdy sam decyduje, jakie akcje są dostępne. MVP przenosi tę
logikę do Presentera, a MVVM do modelu stanu i komend związanego z widokiem. Oba wzorce izolują prezentację,
ale różnią się sposobem aktualizowania UI i kierunkiem współpracy.

**Problem i mechanizm:** Wzorce prezentacji izolują logikę widoku przez Presenter albo ViewModel z wiązaniem
danych.

**Role i współpraca:** Presenter steruje kontraktem View, ViewModel wystawia obserwowalny stan. Widok nie
powinien sam liczyć dostępności przycisku na podstawie prywatnych reguł domeny.

**Przykład:** Presenter ekranu rezerwacji oblicza stan przycisku; ViewModel wystawia observable availability.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
void onSelectionChanged(Item item) {
    boolean canReserve = item.availableUnits() > 0;
    view.showPrice(item.price());
    view.enableReserve(canReserve);
}
```

**Konkretny przypadek użycia:** Formularz rezerwacji

- **Wejście:** Użytkownik wybiera salę bez wolnych terminów.
- **Przebieg:** Presenter aktualizuje widok albo ViewModel wystawia stan disabled.
- **Oczekiwany wynik:** Logika dostępności przycisku ma jedno miejsce możliwe do prześledzenia bez okna.
- **Błąd lub granica:** Reguła ostatecznej rezerwacji musi być ponownie sprawdzona przez domenę, nie tylko UI.

**Kiedy zastosować:** Gdy logika interakcji wymaga testów bez okna. Dobór zależy od frameworka UI; nie przenoś
go automatycznie do każdego backendowego endpointu.

**Koszt i pułapka:** MVP i MVVM mają różny sposób współpracy z widokiem; nie należy ich mechanicznie dodawać
do backendowego CRUD.

**Rozwinięcie i ślad wykonania:** Presenter steruje widokiem przez jawny kontrakt, natomiast ViewModel
udostępnia stan i komendy wiązane przez warstwę UI. W obu przypadkach regułę można testować bez okna. Wybór
zależy od modelu interakcji frameworka; backend może dostarczać DTO bez przyjmowania odpowiedzialności za samą
architekturę ekranu.

**Ćwiczenie sprawdzające:** Porównaj test Presentera bez UI z testem ViewModel przy zmianie danych.

## API Gateway / Gateway Routing

**Intuicja:** Klient nie musi znać wszystkich adresów i sposobów uwierzytelnienia usług. Gateway daje wspólne
wejście i kieruje ścieżki do właściwych właścicieli. Może realizować część polityk transportu, ale nie
przejmuje automatycznie reguł każdego zasobu i nie usuwa potrzeby autoryzacji wewnątrz usług.

**Problem i mechanizm:** Wspólne wejście kieruje ruch do usług i egzekwuje wybrane polityki brzegowe.

**Role i współpraca:** Router wybiera usługę, polityki brzegowe ograniczają ruch i ustalają kontekst, docelowa
usługa obsługuje operację. Wspólne wejście ma własne wymagania dostępności.

**Przykład:** /catalog trafia do Catalog, /orders do Orders; gateway ustala budżet timeoutu i przekazuje
tożsamość.

**Mechanizm w kodzie:** Pseudokod: nazwy oznaczają role i operacje z opisu. To schemat algorytmu lub
interakcji, bez deklaracji implementacji infrastruktury.

```text
request /catalog/* -> Catalog API
request /orders/*  -> Orders API

Gateway: routing, transportowy limit, kontekst tożsamości
Orders: prawo do konkretnego orderId i reguły zamówienia
```

**Konkretny przypadek użycia:** Wspólne wejście API

- **Wejście:** Ścieżki /catalog i /orders trafiają do dwóch usług.
- **Przebieg:** Gateway wybiera trasę, stosuje limit brzegowy i przekazuje zweryfikowany kontekst.
- **Oczekiwany wynik:** Klient nie zna fizycznych adresów instancji.
- **Błąd lub granica:** Usługa Orders nadal musi sprawdzić prawo do konkretnego zamówienia.

**Kiedy zastosować:** Gdy wspólna granica ułatwia dostęp wielu klientów. Unikaj wkładania całego checkoutu do
konfiguracji gateway tylko dlatego, że wszystkie requesty przez niego przechodzą.

**Koszt i pułapka:** Zbyt wiele logiki tworzy centralny monolit. Usługi nadal wymagają autoryzacji na granicy
zasobu.

**Rozwinięcie i ślad wykonania:** Gateway ustala trasę i wspólne polityki transportu, ale właściciel Order
nadal sprawdza prawo do konkretnego zasobu. Utrata gateway wpływa na wszystkie trasy, więc oceniaj replikację
i ograniczenia wspólnych zależności. Nie ukrywaj długiego procesu biznesowego w konfiguracji routingu.

**Ćwiczenie sprawdzające:** Zasymuluj brak jednej usługi i określ status oraz limit oczekiwania.

## Backend for Frontend

**Intuicja:** Telefon i rozbudowany panel webowy mogą potrzebować innych danych oraz innej liczby wywołań.
Jeden uniwersalny endpoint często staje się zbyt ciężki albo zbyt ubogi. BFF daje każdemu rodzajowi interfejsu
własną warstwę kompozycji, zachowując reguły biznesowe u ich właścicieli.

**Problem i mechanizm:** Osobny backend dostosowuje agregację i kontrakt do potrzeb konkretnego klienta.

**Role i współpraca:** Mobile BFF i Web BFF składają dane z portów domenowych. Klient otrzymuje dopasowany
model prezentacji, a nie koniecznie cały publiczny model każdej usługi.

**Przykład:** Mobile BFF zwraca kompaktową kartę zamówienia, a Web BFF bogatszy widok z historią.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
MobileOrderCard card(OrderId id) {
    OrderSummary order = orders.summary(id);
    DeliveryStatus status = delivery.current(id);
    return new MobileOrderCard(order.total(), status.label());
}
```

**Konkretny przypadek użycia:** Karta mobilna i panel web

- **Wejście:** Mobile potrzebuje 3 pól, panel 30 pól i historii.
- **Przebieg:** Dwa BFF komponują odpowiednie modele z publicznych portów domen.
- **Oczekiwany wynik:** Telefon unika ciężkiego payloadu i wielu round-tripów.
- **Błąd lub granica:** Skopiowanie reguły rabatu do obu BFF tworzy dwa konkurencyjne źródła prawdy.

**Kiedy zastosować:** Gdy potrzeby klientów wyraźnie się różnią. Nie duplikuj w BFF reguł ceny, uprawnień do
zasobu i przejść płatności.

**Koszt i pułapka:** Duplikacja kompozycji i więcej komponentów; nie kopiuj reguł ceny i płatności do BFF.

**Rozwinięcie i ślad wykonania:** Mobile BFF może połączyć kilka odczytów w jeden mały payload i ograniczyć
round-tripy klienta. Ceny oraz przejścia płatności nadal należą do właściciela domeny. Gdy oba BFF potrzebują
tej samej reguły, zapytaj o port wspólnej polityki zamiast duplikować ją w prezentacji.

**Ćwiczenie sprawdzające:** Porównaj payload i liczbę round-tripów dla telefonu i panelu administracyjnego.

## API Composition / Gateway Aggregation

**Intuicja:** Dane jednego ekranu mogą należeć do kilku usług. API Composition wywołuje ich kontrakty i łączy
wyniki, zamiast wykonywać SQL po cudzych bazach. Trzeba jednak ustalić wspólny czas oczekiwania i znaczenie
wyniku, gdy jedna część nie odpowie lub dane pochodzą z różnych chwil.

**Problem i mechanizm:** Widok z kilku źródeł powstaje przez wywołania właścicieli danych i połączenie
wyników.

**Role i współpraca:** Kompozytor zna źródła i regułę połączenia po kluczu. Usługi nadal posiadają swoje dane.
Wynik jest modelem odczytu, a nie transakcją obejmującą wszystkie źródła.

**Przykład:** Karta zamówienia łączy Orders i Delivery po orderId przy wspólnym deadline.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
var order = orders.get(id, deadline.remaining());
var shipment = delivery.get(id, deadline.remaining());
return new OrderPage(order, shipment);
// Polityka określa, czy brak shipment daje partial result czy błąd.
```

**Konkretny przypadek użycia:** Podsumowanie dostawy

- **Wejście:** Ekran łączy Orders i Delivery przy budżecie 300 ms.
- **Przebieg:** Kompozytor przekazuje malejący deadline i określa wynik częściowy po timeout Delivery.
- **Oczekiwany wynik:** Klient widzi status zamówienia i jawny brak części dostawy.
- **Błąd lub granica:** Wyniki z dwóch chwil nie są atomowym snapshotem całego procesu.

**Kiedy zastosować:** Dla małego fan-out i odczytów tolerujących różne momenty obserwacji. Przy dużej liczbie
źródeł lub rygorystycznym latency osobna projekcja może być lepsza.

**Koszt i pułapka:** Fan-out zwiększa ryzyko ogona latency; wynik nie jest transakcyjnym snapshotem wielu
usług.

**Rozwinięcie i ślad wykonania:** Kompozytor rozpoczyna od wspólnego deadline i polityki częściowego wyniku.
Odpowiedź Orders w chwili t1 oraz Delivery w chwili t2 nie tworzą wspólnego snapshotu ACID. Klient powinien
wiedzieć, czy brak statusu to brak danych, brak uprawnień, czy degradacja po timeout.

**Ćwiczenie sprawdzające:** Ustal politykę częściowego wyniku przy timeout Delivery.

## Strangler Fig / Branch by Abstraction

**Intuicja:** Wymiana dużego systemu jednym krokiem zwiększa ryzyko. Strangler stopniowo kieruje wybrane
operacje do nowego rozwiązania, pozostawiając resztę w starym. Branch by Abstraction daje podobny punkt
przełączenia wewnątrz kodu za wspólnym kontraktem. Routing jest prostszy niż migracja własności danych.

**Problem i mechanizm:** Nowa implementacja stopniowo przejmuje ścieżki za stabilnym punktem wejścia;
abstrakcja umożliwia przełączanie wariantów.

**Role i współpraca:** Granica wybiera wariant dla zakresu ruchu. Stary i nowy adapter realizują uzgodniony
kontrakt. Plan migracji określa, kto zapisuje dane i co oznacza cofnięcie po nowych zapisach.

**Przykład:** Router wysyła nowe kategorie produktów do nowego Catalog, resztę do starego systemu.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
CatalogPort chosen = migratedCategories.contains(category)
        ? newCatalog
        : legacyCatalog;
return chosen.findProduct(sku);
```

**Konkretny przypadek użycia:** Wymiana starego katalogu

- **Wejście:** Nowa usługa przejmuje tylko kategorię books.
- **Przebieg:** Stabilna granica routingu kieruje books do nowego modelu, resztę do legacy.
- **Oczekiwany wynik:** Migrację można obserwować na ograniczonym zakresie ruchu.
- **Błąd lub granica:** Rollback routingu nie odwraca zapisów wykonanych już w nowym formacie.

**Kiedy zastosować:** Gdy potrzebujesz etapowej wymiany z obserwacją i możliwością zatrzymania. Nie zakładaj,
że przełączenie flagi przywróci również poprzedni format danych.

**Koszt i pułapka:** Migracja właściciela danych jest trudniejsza niż routingu; rollback kodu nie zawsze
odwraca nowe zapisy.

**Rozwinięcie i ślad wykonania:** Wskaż stabilny punkt przełączenia i właściciela danych dla migrowanego
fragmentu. Przełączenie odczytu zwykle można odwrócić łatwiej niż nowy zapis. Branch by Abstraction może
kierować wariantem wewnątrz procesu, Strangler ruchem między systemami; oba wymagają planu obsługi danych już
zmienionych.

**Ćwiczenie sprawdzające:** Zaprojektuj przejęcie jednego endpointu i plan odwrócenia po zapisach.

## Sidecar / Ambassador

**Intuicja:** Każda instancja aplikacji może potrzebować lokalnego pomocnika: proxy połączeń albo kolektora
telemetrii. Sidecar działa obok głównego procesu i bierze na siebie wybraną funkcję techniczną. Ambassador
jest wariantem pośredniczenia w dostępie do zewnętrznej usługi; nie musi zawierać reguł domeny.

**Problem i mechanizm:** Proces pomocniczy obok aplikacji realizuje funkcję infrastrukturalną, np. proxy
połączeń lub eksport logów.

**Role i współpraca:** Aplikacja używa lokalnego kontraktu, proces pomocniczy realizuje transport lub
zbieranie danych, platforma zarządza ich wspólnym lifecycle. Awaria pomocnika pozostaje widoczna dla aplikacji
przez granicę czasu i błędu.

**Przykład:** Aplikacja wysyła ruch przez lokalne proxy z mTLS; collector odczytuje jej logi.

**Mechanizm w kodzie:** Pseudokod: nazwy oznaczają role i operacje z opisu. To schemat algorytmu lub
interakcji, bez deklaracji implementacji infrastruktury.

```text
instancja:
    app --------> lokalne proxy --------> zewnętrzna usługa
    app logi ---> lokalny collector ----> backend telemetrii

Timeout app nadal ogranicza oczekiwanie na proxy.
```

**Konkretny przypadek użycia:** Lokalny eksport telemetrii

- **Wejście:** Każda instancja ma aplikację i collector.
- **Przebieg:** Aplikacja przekazuje logi lokalnie, pomocnik eksportuje je do backendu.
- **Oczekiwany wynik:** Wspólna funkcja techniczna ma osobny lifecycle i konfigurację.
- **Błąd lub granica:** Collector konkuruje o CPU i pamięć; jego awaria nie powinna nieświadomie blokować
  krytycznej operacji.

**Kiedy zastosować:** Gdy wspólna funkcja infrastruktury ma niezależne narzędzie i konfigurację. Uwzględnij
dodatkowy koszt procesu, hop i zachowanie przy częściowej awarii instancji.

**Koszt i pułapka:** Dodatkowy hop, pamięć i zależność lifecycle; sidecar nie przenosi odpowiedzialności za
semantykę domeny.

**Rozwinięcie i ślad wykonania:** Sidecar dziedziczy część lifecycle instancji i konkuruje o CPU oraz pamięć
tego samego hosta. Gdy pośrednik przestaje odpowiadać, aplikacja musi skończyć oczekiwanie własnym timeoutem.
Ambassador upraszcza dostęp do zewnętrznej usługi, ale nie zmienia jej semantyki retry ani dostępności.

**Ćwiczenie sprawdzające:** Opisz zachowanie aplikacji, gdy proxy działa, ale jego upstream nie odpowiada.

## Service Mesh

**Intuicja:** Przy wielu usługach konfiguracja mTLS, routingu i obserwacji w każdym SDK zaczyna się powtarzać.
Service Mesh przenosi część tych polityk do wspólnej warstwy ruchu. Oddziela wykonywanie polityk przy
wywołaniu od zarządzania nimi, ale nie zna automatycznie semantyki biznesowej operacji.

**Problem i mechanizm:** Wspólna warstwa data plane i control plane zarządza ruchem między usługami.

**Role i współpraca:** Data plane pośredniczy w ruchu, control plane dostarcza konfigurację. Aplikacja nadal
odpowiada za idempotencję, deadline całego procesu i sens retry.

**Przykład:** Proxy egzekwują mTLS i limity połączeń, control plane rozsyła konfigurację.

**Mechanizm w kodzie:** Pseudokod: nazwy oznaczają role i operacje z opisu. To schemat algorytmu lub
interakcji, bez deklaracji implementacji infrastruktury.

```text
control plane -- polityki --> proxy A, proxy B
app A --> proxy A ===== mTLS ===== proxy B --> app B

Polityka sieci nie potwierdza, czy komenda biznesowa wykonała efekt.
```

**Konkretny przypadek użycia:** Wspólne mTLS między usługami

- **Wejście:** Dziesięć usług potrzebuje tożsamości transportowej i obserwacji ruchu.
- **Przebieg:** Control plane dostarcza polityki, data plane egzekwuje je na połączeniach.
- **Oczekiwany wynik:** Zespół nie implementuje wszystkich tych polityk w każdym SDK.
- **Błąd lub granica:** Retry mesh i aplikacji nie mogą mnożyć prób bez wspólnego budżetu.

**Kiedy zastosować:** Gdy wspólne polityki między wieloma usługami uzasadniają złożoność operacyjną. Retry
aplikacji i mesh muszą dzielić uzgodniony budżet, a nie mnożyć niezależne próby.

**Koszt i pułapka:** Retry na poziomie mesh może powielić skutki i pomnożyć retry aplikacji; potrzebna jest
jedna polityka budżetu.

**Rozwinięcie i ślad wykonania:** Data plane wykonuje polityki ruchu, control plane dostarcza konfigurację.
Rozdziel awarię sterowania od awarii istniejących połączeń. Jeśli proxy i SDK ponawiają trzy razy, jedna
operacja może mieć dziewięć prób; koordynacja budżetu należy do projektu całego przepływu, nie pojedynczego
narzędzia.

**Ćwiczenie sprawdzające:** Policz maksymalną liczbę prób przy retry w SDK, usłudze i proxy.

## Service Discovery / Service Registry

**Intuicja:** Adresy instancji zmieniają się po skalowaniu i awarii. Service Discovery oddziela nazwę usługi
od bieżącej listy jej lokalizacji. Registry może być odpytywane przez klienta albo przez pośrednika, ale
odpowiedź zawsze jest obserwacją, która za moment może być nieaktualna.

**Problem i mechanizm:** Instancja usługi znajduje aktualne adresy przez registry albo mechanizm platformy.

**Role i współpraca:** Registry przechowuje lokalizacje, resolver je pobiera, selector wybiera instancję.
Timeout i obsługa nieaktualnej informacji nadal należą do ścieżki wywołania.

**Przykład:** Klient pobiera listę instancji Inventory i wybiera zdrową; alternatywnie robi to load balancer
po DNS.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
List<Endpoint> candidates = registry.resolve("inventory");
Endpoint selected = balancer.choose(candidates);
return client.call(selected, request, remainingBudget);
```

**Konkretny przypadek użycia:** Zmiana adresu instancji

- **Wejście:** Inventory skaluje się z 3 do 5 instancji.
- **Przebieg:** Resolver odczytuje registry, balancer wybiera aktualnego kandydata.
- **Oczekiwany wynik:** Klient używa nazwy usługi zamiast stałej listy IP.
- **Błąd lub granica:** Wybrana instancja może umrzeć po resolve; timeout nadal jest potrzebny.

**Kiedy zastosować:** Gdy instancje są dynamiczne. Rozróżnij odnowienie wpisu od gwarancji sukcesu requestu;
zdrowa instancja może umrzeć tuż po wyborze.

**Koszt i pułapka:** Cache registry i DNS może być nieaktualny; health check nie dowodzi powodzenia
konkretnego requestu.

**Rozwinięcie i ślad wykonania:** Adres odczytany z registry jest tylko obserwacją: instancja może umrzeć tuż
po wyborze. Klient potrzebuje timeoutu połączenia i polityki nieaktualnego wpisu. Rozróżnij cache odkrywania
instancji od cache wyniku biznesowego; mają inne okresy ważności i zachowanie po błędzie.

**Ćwiczenie sprawdzające:** Usuń jedną instancję i określ czas, przez który klienci mogą nadal jej używać.

## Cell-based Architecture / Deployment Stamps

**Intuicja:** Jedna wspólna instalacja może mieć zbyt duży zasięg awarii. Architektura komórkowa dzieli system
na podobne, ograniczone jednostki obsługujące podzbiory klientów lub danych. Awaria jednej komórki powinna
pozostawić pozostałe zdolne do pracy, jeśli krytyczne zależności również są odizolowane.

**Problem i mechanizm:** System dzieli się na izolowane komórki z ograniczonym zakresem danych i awarii.

**Role i współpraca:** Router przypisuje tenant do cell, każda cell ma własne usługi i zasoby. Mapa własności
określa miejsce zapisu i migrację klienta między komórkami.

**Przykład:** TenantId kieruje do komórki z własnymi Orders, bazą i kolejką; awaria nie blokuje innych
komórek.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
CellId cell = ownership.cellFor(verifiedTenant);
Endpoint endpoint = cells.ordersEndpoint(cell);
return client.submit(endpoint, verifiedTenant, command);
```

**Konkretny przypadek użycia:** Izolacja dużych tenantów

- **Wejście:** 200 tenantów jest rozdzielonych na 4 komórki po około 50.
- **Przebieg:** Mapa własności kieruje request do właściwej cell z jej bazą i kolejką.
- **Oczekiwany wynik:** Awaria jednej cell ma ograniczony zakres klientowski.
- **Błąd lub granica:** Globalny wspólny store lub routing może nadal zablokować wszystkie komórki.

**Kiedy zastosować:** Gdy chcesz ograniczać blast radius i skalować powtarzalne instalacje. Wspólna globalna
baza lub routing może nadal znieść izolację całej topologii.

**Koszt i pułapka:** Routing, przenoszenie tenantów i operacje globalne komplikują projekt; wspólny dependency
może znieść izolację.

**Rozwinięcie i ślad wykonania:** Żądanie najpierw wyznacza komórkę według tenanta, a dopiero potem trafia do
usług tej komórki. Przejęcie tenanta wymaga jednego aktywnego właściciela zapisu podczas relokacji. Wspólny
globalny store tożsamości lub routing może nadal stanowić punkt awarii całego systemu, mimo izolowanych baz
komórek.

**Ćwiczenie sprawdzające:** Wskaż wszystkie wspólne komponenty i maksymalny blast radius ich awarii.
