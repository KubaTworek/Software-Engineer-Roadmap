# Persystencja i dostęp do danych

[Moduł](../../README.md) · [Indeks](../../INDEKS.md) · [Materiały](../../MATERIALY.md#poeaa)

Wzorce aplikacji enterprise, SQL i cache. **20 kart.**

**Status przykładów:** moduł dokumentacyjny. Fragmenty Java, SQL i HTTP oraz pseudokod są objaśnieniami
mechanizmu; kompletne modele i scenariusze poprawności również znajdują się w Markdown. Nie ma osobnej
aplikacji, buildu Java ani testów uruchamianych przez Maven.

**Rozbudowany kod i omówienie:** [Unit of Work](../../labs/01-domain-and-persistence.md) i
[TTL/LRU cache](../../labs/03-resilience-and-cache.md). Studium w Markdown rozwija wskazany podzbiór wzorców
tej kategorii.

**Jak rozumieć tę kategorię:** Persystencja dotyczy czegoś więcej niż zapisu pól: trzeba ustalić właściciela
logiki, reprezentację danych, zakres śledzenia zmian i sposób obsługi konfliktu. Druga grupa wzorców utrzymuje
kopie odczytowe, więc pytaj o źródło prawdy, dopuszczalną starość i naprawę rozjazdu. Fragmenty SQL są
szablonami, a fragmenty Java pokazują granice odpowiedzialności, bez deklaracji wspólnego ORM lub silnika
bazy.

## Spis kart

- [Transaction Script](#transaction-script)
- [Domain Model / Rich Domain Model](#domain-model--rich-domain-model)
- [Table Module](#table-module)
- [Active Record](#active-record)
- [Data Mapper](#data-mapper)
- [Table Data Gateway / Row Data Gateway / DAO](#table-data-gateway--row-data-gateway--dao)
- [Unit of Work](#unit-of-work)
- [Identity Map](#identity-map)
- [Lazy Load / Ghost / Virtual Proxy](#lazy-load--ghost--virtual-proxy)
- [Data Transfer Object / Projection](#data-transfer-object--projection)
- [Optimistic Offline Lock / Compare-and-set](#optimistic-offline-lock--compare-and-set)
- [Pessimistic Offline Lock / Lock before Change](#pessimistic-offline-lock--lock-before-change)
- [Cache-aside / Lazy Caching](#cache-aside--lazy-caching)
- [Read-through Cache](#read-through-cache)
- [Write-through Cache](#write-through-cache)
- [Write-behind / Write-back Cache](#write-behind--write-back-cache)
- [Refresh-ahead / Stale-while-revalidate](#refresh-ahead--stale-while-revalidate)
- [Materialized View / Denormalized Read Model](#materialized-view--denormalized-read-model)
- [Keyset / Seek Pagination](#keyset--seek-pagination)
- [Expand–Migrate–Contract / Parallel Change](#expandmigratecontract--parallel-change)

## Transaction Script

**Intuicja:** Jeżeli przypadek użycia ma kilka prostych sprawdzeń i zapisów, procedura może być najbardziej
bezpośrednią reprezentacją. Transaction Script skupia ten przebieg w jednej operacji. Nie potrzebuje
obiektowego modelu każdej reguły, ale wymaga poprawnej granicy transakcji i ochrony decyzji przed współbieżną
zmianą.

**Problem i mechanizm:** Pojedyncza procedura obsługuje przypadek użycia i jego transakcję bez rozbudowanego
modelu obiektowego.

**Role i współpraca:** Procedura obsługuje jeden zamiar, gateway wykonuje SQL, transakcja obejmuje powiązane
zapisy. Wyjątek kończy całą uzgodnioną jednostkę pracy.

**Przykład:** approveInvoice sprawdza status i wykonuje dwa UPDATE w jednej transakcji lokalnej.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
transaction.run(() -> {
    InvoiceRow row = invoices.lock(invoiceId);
    requireNew(row.status());
    invoices.markApproved(invoiceId);
    approvals.append(invoiceId, actorId);
});
```

**Konkretny przypadek użycia:** Prosta akceptacja faktury

- **Wejście:** Faktura NEW ma zostać APPROVED i otrzymać wpis zatwierdzenia.
- **Przebieg:** Jedna procedura blokuje lub wersjonuje rekord i wykonuje dwa zapisy w transakcji.
- **Oczekiwany wynik:** Oba skutki pojawiają się razem.
- **Błąd lub granica:** Awaria drugiego zapisu nie może zostawić APPROVED bez śladu zatwierdzenia.

**Kiedy zastosować:** Dla prostego CRUD i nieskomplikowanych reguł. Gdy warunki wracają w wielu procedurach i
zależą od lifecycle, Domain Model może ograniczyć ich powielanie.

**Koszt i pułapka:** Przy wielu przecinających się regułach procedury powielają logikę; dla prostego CRUD
bywają najlepszą opcją.

**Rozwinięcie i ślad wykonania:** Procedura rozpoczyna transakcję, odczytuje wymagany stan, sprawdza warunki i
wykonuje zapisy. Każdy warunek powinien być związany z właściwą izolacją lub blokadą: sam if(status == NEW)
nie chroni przed równoczesnym zatwierdzeniem. Przy prostym przepływie nie trzeba dodatkowo tworzyć
rozbudowanej hierarchii encji.

**Ćwiczenie sprawdzające:** Porównaj prosty approve z modelem faktury zawierającym reguły korekt.

## Domain Model / Rich Domain Model

**Intuicja:** Kiedy reguły zaczynają dotyczyć stanu i historii obiektu, kolejne procedury mogą je kopiować.
Domain Model umieszcza dane i zachowanie w obiektach, które potrafią bronić własnej poprawności. Serwis nie
musi za każdym razem pamiętać, jaką kombinację setterów wolno wywołać.

**Problem i mechanizm:** Stan i zachowanie domenowe współpracują w obiektach zamiast wyłącznie procedurach
operujących na rekordach.

**Role i współpraca:** Invoice zna dozwolone przejście, pozycje i sposób liczenia sumy. Aplikacja organizuje
I/O, a mapper zapisuje wynik. Encja z samymi getterami nie realizuje tej intencji.

**Przykład:** Invoice.approve() pilnuje dozwolonego przejścia oraz salda pozycji.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
Invoice invoice = invoices.load(id);
invoice.addLine(product, quantity, unitPrice);
invoice.approve(limit);
invoices.save(invoice, expectedVersion);
```

**Konkretny przypadek użycia:** Złożony lifecycle faktury

- **Wejście:** Występują pozycje, korekty i wiele legalnych przejść.
- **Przebieg:** Invoice posiada zachowanie addLine i approve, aplikacja tylko koordynuje I/O.
- **Oczekiwany wynik:** Reguły nie są kopiowane po kontrolerach.
- **Błąd lub granica:** Sama klasa z getterami nie zapobiega nielegalnej kombinacji setterów.

**Kiedy zastosować:** Dla złożonych, powtarzanych reguł i legalnych przejść. Prosty przypadek bez takiego
zachowania może nie potrzebować dodatkowej hierarchii domenowej.

**Koszt i pułapka:** Mapowanie do relacyjnej bazy wymaga decyzji; sam zestaw getterów nie przenosi reguł do
modelu.

**Rozwinięcie i ślad wykonania:** Order.confirm pilnuje reguł na obiekcie, a repozytorium kontroluje wersję
podczas zapisu. Model obiektowy nie zastępuje ograniczeń bazy; oba mogą bronić tego samego niezmiennika na
innych granicach. Zmiana prywatnego pola przez mapper musi zachować legalność odtworzonego stanu.

**Ćwiczenie sprawdzające:** Przenieś warunek legalnego przejścia z kontrolera do Invoice.

## Table Module

**Intuicja:** Nie każda logika naturalnie działa na pojedynczej encji. Naliczanie danych dla całej tabeli albo
zestawu rekordów może być czytelniejsze jako operacja zbiorcza. Table Module skupia reguły związane z danym
zbiorem, korzystając z jego tabelarycznej reprezentacji i operacji na wierszach.

**Problem i mechanizm:** Jeden obiekt organizuje logikę dla całego zbioru wierszy tabeli lub widoku.

**Role i współpraca:** Moduł otrzymuje zbiór wierszy i wykonuje reguły dla tego zbioru. Nie tworzy koniecznie
osobnej instancji zachowania dla każdego pracownika lub rekordu.

**Przykład:** PayrollModule nalicza potrącenia dla DataSet pracowników, wykonując operacje zbiorcze.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
PayrollTable rows = payroll.loadFor(month);
payrollModule.calculateTaxes(rows);
payrollModule.applyDeductions(rows);
payroll.saveBatch(rows);
```

**Konkretny przypadek użycia:** Naliczanie wypłat porcją

- **Wejście:** Do naliczenia jest 10000 wierszy pracowników.
- **Przebieg:** Table Module operuje na zbiorze i przygotowuje paczkowy zapis.
- **Oczekiwany wynik:** Reguła tabelaryczna pozostaje czytelna bez osobnego obiektu zachowania na każdy
  wiersz.
- **Błąd lub granica:** Bogaty indywidualny lifecycle może uzasadniać osobny model encji dla części reguł.

**Kiedy zastosować:** Dla tabelarycznego przetwarzania i operacji batch. Gdy pojedynczy rekord ma bogaty
lifecycle i własną tożsamość, model encji może lepiej oddać odpowiedzialność.

**Koszt i pułapka:** Reguły pojedynczych encji i tożsamość obiektów są mniej naturalne; wzorzec pasuje do
przetwarzania tabelarycznego.

**Rozwinięcie i ślad wykonania:** Logika operuje na zbiorze rekordów, więc łatwo wykorzystać przetwarzanie
porcjami i agregacje zamiast tysiąca osobnych round-tripów. Porównuj koszt całego zbioru z wygodą obiektu per
wiersz. Jeśli pracownik ma złożony indywidualny lifecycle, część reguł może lepiej pasować do encji.

**Ćwiczenie sprawdzające:** Porównaj naliczenie 10000 wypłat w module tabeli i po jednej encji.

## Active Record

**Intuicja:** W prostym modelu obiekt może jednocześnie reprezentować wiersz i znać sposób swojego zapisu.
Active Record daje klientowi wygodne metody find i save blisko danych. Cena tej prostoty to zależność modelu
od persystencji i trudniejsze oddzielenie samych reguł od infrastruktury.

**Problem i mechanizm:** Obiekt reprezentuje wiersz i sam zna operacje swojej persystencji.

**Role i współpraca:** User jest reprezentacją rekordu z operacjami dostępu do danych. Klient zmienia stan i
wywołuje save; obiekt lub jego baza techniczna obsługuje mapowanie.

**Przykład:** User.find(id) zwraca obiekt, user.save() wykonuje zapis jego pól.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
User user = User.find(userId);
user.changeDisplayName("Ada");
user.save(); // Zapis jest częścią API tego obiektu.
```

**Konkretny przypadek użycia:** Prosty panel użytkowników

- **Wejście:** Zmiana displayName dotyczy pojedynczego rekordu.
- **Przebieg:** User.find, changeDisplayName i save realizują lokalny scenariusz.
- **Oczekiwany wynik:** API obiektu jest wygodne dla prostego CRUD.
- **Błąd lub granica:** Reguły są związane z persystencją; sam scenariusz w pamięci nie dowodzi SQL.

**Kiedy zastosować:** Dla prostych modeli danych i szybkich aplikacji CRUD. Jeśli domena ma działać bez bazy,
porównaj z Data Mapper i portem repozytorium.

**Koszt i pułapka:** Domena zależy od bazy; encja JPA sama w sobie nie musi być Active Record, jeśli nie
posiada operacji dostępu do danych.

**Rozwinięcie i ślad wykonania:** user.save() łączy obiekt z mechanizmem zapisu, więc odtworzenie pełnego
zachowania w teście może wymagać store. W Data Mapper ta zależność jest przeniesiona do osobnego komponentu.
Użycie adnotacji ORM samo nie rozstrzyga, który z tych dwóch kontraktów udostępnia aplikacja.

**Ćwiczenie sprawdzające:** Wskaż granicę testu jednostkowego przy regule uruchamiającej save().

## Data Mapper

**Intuicja:** Model domenowy i układ tabel rzadko muszą mieć dokładnie tę samą postać. Data Mapper przenosi
odpowiedzialność ich uzgadniania do osobnej warstwy. Dzięki temu obiekt biznesowy nie wie, z ilu wierszy
powstał i przez jakie instrukcje zostanie zapisany.

**Problem i mechanizm:** Osobna warstwa przenosi stan między modelem obiektowym a bazą, bez wiedzy domeny o
SQL.

**Role i współpraca:** Mapper tworzy obiekt z Row i przygotowuje Row z obiektu. Store lub gateway realizuje
fizyczny dostęp. Unit of Work może nadzorować zbiorczy zapis zmian.

**Przykład:** OrderMapper mapuje root i pozycje na rekordy; model Order nie importuje biblioteki bazy.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
OrderRow row = gateway.find(orderId);
Order order = mapper.toDomain(row);
order.confirm(limit);
gateway.update(mapper.toRow(order), row.version());
```

**Konkretny przypadek użycia:** Odtworzenie Order

- **Wejście:** Root i pozycje są zapisane w trzech tabelach.
- **Przebieg:** Mapper składa model i tłumaczy go z powrotem na rekordy podczas zapisu.
- **Oczekiwany wynik:** Domena nie importuje biblioteki ORM.
- **Błąd lub granica:** Błędne pole mappera może przejść bez wyjątku, dlatego ważny jest oczekiwany round-trip
  wartości.

**Kiedy zastosować:** Gdy chcesz odizolować model od schematu i szczegółów narzędzia. Koszt mapowania warto
ograniczać do rzeczywistych granic, nie kopiować ten sam model przez wiele pustych warstw.

**Koszt i pułapka:** Mapowanie i śledzenie zmian są dodatkowym kosztem; rozjazd modelu i schematu wymaga
testów.

**Rozwinięcie i ślad wykonania:** UnitOfWorkLab odczytuje Row i tworzy Customer, a przy commit mapuje
zmieniony Customer na nowy Row. Obiekt domeny nie zna struktury mapy store. Test round-trip powinien sprawdzać
wartości i ID, nie tylko liczbę wywołań mappera, bo błędna kolumna może dać pozornie udany zapis.

**Ćwiczenie sprawdzające:** Zapisz i odtwórz agregat z dwiema pozycjami, zachowując tożsamości.

## Table Data Gateway / Row Data Gateway / DAO

**Intuicja:** Powtarzany SQL nie powinien być rozproszony po kontrolerach i regułach domeny. Gateway lub DAO
skupia techniczne operacje dostępu do danych. Table Data Gateway obsługuje zbiór tabeli, Row Data Gateway
pojedynczy wiersz; DAO jest szerszą nazwą obiektu dostępu, nie automatycznie kolekcją agregatów.

**Problem i mechanizm:** Dedykowany obiekt kapsułkuje dostęp do tabeli, wiersza albo zestawu operacji
technicznych.

**Role i współpraca:** Gateway ukrywa zapytania i parametry, mapper buduje obiekty, klient zleca konkretny
odczyt. Kontrakt powinien określać, czy zwracane są wiersze, DTO czy agregaty.

**Przykład:** OrdersGateway.findRowsByCustomer wykonuje SQL, a mapper dopiero tworzy obiekty domenowe.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
interface OrderRows {
    OrderRow find(long id);
    List<OrderRow> findByCustomer(long customerId);
    int updateStatus(long id, String status, long version);
}
```

**Konkretny przypadek użycia:** Techniczne query po kliencie

- **Wejście:** Panel potrzebuje wierszy zamówień customer42.
- **Przebieg:** Gateway wykonuje parametryzowany SELECT i oddaje uzgodnioną reprezentację.
- **Oczekiwany wynik:** SQL ma jedno miejsce zamiast kopiowania w kontrolerach.
- **Błąd lub granica:** DAO nie staje się automatycznie kolekcją agregatów i nie powinno ujawniać dowolnego
  SQL domenie.

**Kiedy zastosować:** Gdy chcesz centralizować SQL i techniczne operacje store. Jeśli klient potrzebuje języka
domenowego, Repository może być właściwszym portem nad tym adapterem.

**Koszt i pułapka:** DAO i Repository bywają mylone: dostęp do tabel nie jest automatycznie kolekcją
agregatów.

**Rozwinięcie i ślad wykonania:** Gateway może zwracać rekordy dobrane pod zapytanie, a Repository agregaty
dobrane pod reguły domenowe. Jeśli kontroler zaczyna znać nazwy kolumn i budować SQL przez gateway, szczegóły
persystencji przeszły za granicę. Dla raportu jawny port odczytu z DTO bywa wystarczający.

**Ćwiczenie sprawdzające:** Rozdziel techniczne zapytanie SQL od domenowego Orders.byId().

## Unit of Work

**Intuicja:** Jedna operacja może zmienić kilka obiektów i nie powinna zapisać tylko połowy. Unit of Work
śledzi, co w określonym zakresie odczytano, dodano, zmieniono lub usunięto, a potem koordynuje wspólny zapis.
Pozwala także nie wysyłać bez potrzeby zmian dla obiektów, które pozostały takie same.

**Problem i mechanizm:** Zbiera nowe, zmienione i usunięte obiekty oraz zapisuje je w ramach jednej
transakcji.

**Role i współpraca:** Session przechowuje oryginały i bieżące obiekty, commit ustala dirty set, store
sprawdza wersje oraz publikuje wynik. W przykładzie kopia stanu modeluje lokalną atomowość.

**Przykład:** Persistence context śledzi zmiany Order i Lines, flush wysyła SQL, a commit zatwierdza
transakcję.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
try (Session session = new Session(store)) {
    Customer first = session.get(1);
    Customer second = session.get(2);
    first.changeEmail("a@example.test");
    second.changeEmail("b@example.test");
    session.commit(() -> { });
}
```

**Konkretny przypadek użycia:** Zmiana dwóch klientów

- **Wejście:** Sesja zmienia email ID 1 i ID 2, drugi rekord ma konflikt wersji.
- **Przebieg:** Unit of Work zbiera dirty obiekty i waliduje wszystkie przed publikacją.
- **Oczekiwany wynik:** Konflikt ID 2 nie zapisuje częściowo ID 1.
- **Błąd lub granica:** Lokalna kopia mapy modeluje atomowość, nie fsync i recovery DB.

**Kiedy zastosować:** Gdy wspólna transakcja obejmuje wiele obiektów i śledzenie zmian ma wartość. Zakres
powinien być krótki, aby nie utrzymywać stale rosnącej pamięci i starych odczytów.

**Koszt i pułapka:** Flush nie oznacza commit; duży kontekst zwiększa pamięć i koszt dirty checking.

**Rozwinięcie i ślad wykonania:** Session porównuje bieżący email z oryginalnym Row i zbiera tylko dirty
obiekty. Store najpierw waliduje wszystkie wersje i buduje kopię, potem publikuje cały snapshot. Konflikt
drugiego wiersza nie może pozostawić pierwszego zapisanego. Jest to model lokalnego commitu, bez flush i
protokołu odzyskiwania bazy.

**Ćwiczenie sprawdzające:** Pokaż rollback po flush oraz czyszczenie kontekstu w imporcie porcjami.

## Identity Map

**Intuicja:** Dwa odczyty tego samego rekordu w jednej operacji mogą stworzyć dwie niezależne kopie i
sprzeczne zmiany. Identity Map utrzymuje jedną reprezentację obiektową dla ID w zdefiniowanym zakresie.
Kolejne ścieżki tej samej jednostki pracy dostają więc ten sam obiekt, zamiast koordynować rozbieżne lokalne
kopie.

**Problem i mechanizm:** W jednym zakresie pracy dany klucz bazy ma jedną reprezentację obiektową.

**Role i współpraca:** Mapa wiąże klucz z obiektem, loader tworzy brakujący obiekt, Session wyznacza zakres.
Druga sesja ma odrębną mapę i może obserwować inny stan.

**Przykład:** Dwa odczyty OrderId 7 w tej samej sesji zwracają tę samą instancję, więc zmiany nie rozchodzą
się na dwa obiekty.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
Customer load(long id) {
    return identityMap.computeIfAbsent(id, key -> {
        Row row = store.read(key);
        return mapper.toCustomer(row);
    });
}
```

**Konkretny przypadek użycia:** Wielokrotny odczyt w sesji

- **Wejście:** Dwa fragmenty use case wczytują Customer ID 1.
- **Przebieg:** Identity Map zwraca jedną instancję w tej samej sesji.
- **Oczekiwany wynik:** Zmiany nie rozchodzą się na dwie lokalne kopie tego samego ID.
- **Błąd lub granica:** Druga sesja ma własny obiekt; mapa nie daje globalnie świeżego cache.

**Kiedy zastosować:** Gdy jeden graf pracy wielokrotnie odwołuje się do tych samych encji. Nie zamieniaj mapy
w globalny cache, bo tożsamość lokalna nie zapewnia aktualności między transakcjami.

**Koszt i pułapka:** Mapa nie jest globalnym cache; długie życie grozi starym stanem i retencją pamięci.

**Rozwinięcie i ślad wykonania:** Pierwszy get(1) tworzy Customer i umieszcza go w identityMap. Drugi odczyt
tego samego ID w tej sesji oddaje tę samą instancję, więc reguły nie pracują na rozbieżnych kopiach. Druga
sesja ma inny obiekt i własną wersję; globalna mapa nie zastąpi izolacji transakcji.

**Ćwiczenie sprawdzające:** Porównaj tożsamość w jednej sesji i w dwóch niezależnych sesjach.

## Lazy Load / Ghost / Virtual Proxy

**Intuicja:** Ładowanie całego grafu zamówienia bywa zbędne, jeśli ekran potrzebuje tylko numeru i statusu.
Lazy Load odkłada pobranie części danych do pierwszego użycia. Wymaga jednak świadomego kontraktu, bo
zwyczajnie wyglądający odczyt pola może wtedy wykonać I/O i zawieść.

**Problem i mechanizm:** Powiązane dane pobiera się dopiero przy faktycznym użyciu.

**Role i współpraca:** Proxy, uchwyt lub obiekt ghost reprezentuje jeszcze niezaładowaną część. Loader
pozyskuje ją przy dostępie, a właściciel sesji określa dostępność infrastruktury.

**Przykład:** Order.lines jest ładowane po wywołaniu lines(); widok podsumowania nie musi go pobierać.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
Order order = orders.loadHeader(id);
show(order.status()); // Bez potrzeby pobrania pozycji.
List<Line> lines = order.lines(); // Ten dostęp może uruchomić SQL.
```

**Konkretny przypadek użycia:** Lista bez ciężkich relacji

- **Wejście:** Lista 100 zamówień potrzebuje nagłówka, szczegóły pozycji tylko po kliknięciu.
- **Przebieg:** Lazy Load odkłada relację do chwili użycia.
- **Oczekiwany wynik:** Widok listy może uniknąć pobierania wszystkich pozycji.
- **Błąd lub granica:** Iteracja po lazy relacji każdego z 100 rekordów może wytworzyć N+1.

**Kiedy zastosować:** Gdy duża część danych często nie jest używana. Dla list i raportów jawna projekcja lub
fetch plan może lepiej pokazać koszt niż wiele ukrytych lazy getterów.

**Koszt i pułapka:** N+1, zamknięta sesja i ukryte I/O mogą zaskoczyć; LazyImage pokazuje lokalny mechanizm
zastępcy, nie ORM.

**Rozwinięcie i ślad wykonania:** Najpierw sprawdź, czy wynik odczytu rzeczywiście potrzebuje relacji. Widok
listy może korzystać z projekcji, a szczegóły z jawnego fetch planu. Pomiar N+1 liczy wykonane zapytania dla
całego przebiegu, nie deklarowane adnotacje ani liczbę metod repozytorium.

**Ćwiczenie sprawdzające:** Policz SQL dla listy 100 zamówień z odczytem pozycji.

## Data Transfer Object / Projection

**Intuicja:** Publiczna odpowiedź API nie powinna przypadkowo ujawniać każdego pola i relacji encji bazy. DTO
nadaje danym kształt potrzebny na konkretnej granicy. Projection jest szczególnym modelem odczytu dobranym do
zapytania; może powstać bez budowania całego agregatu i jego zachowania.

**Problem i mechanizm:** Osobny kształt danych przekracza granicę lub odpowiada na konkretny odczyt, bez
ujawniania modelu persystencji.

**Role i współpraca:** Query wybiera dane, mapper tworzy OrderSummary, adapter transportu serializuje ten
kontrakt. Reguły aktualizacji zamówienia nadal należą do modelu zapisu.

**Przykład:** OrderSummary(orderId, total, status) wraca z GET /orders bez relacji lazy i pól technicznych
encji.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
record OrderSummary(String id, long totalCents, String status) { }

OrderSummary summary = query.findSummary(orderId);
return response.ok(summary);
```

**Konkretny przypadek użycia:** Publiczne podsumowanie

- **Wejście:** GET /orders ma zwracać ID, total i status, bez pól wewnętrznych.
- **Przebieg:** Query i mapper tworzą OrderSummary.
- **Oczekiwany wynik:** Kontrakt nie ujawnia przypadkiem nowego pola encji.
- **Błąd lub granica:** Serializacja encji z lazy getterem może wykonać ukryte I/O albo wyciec dane.

**Kiedy zastosować:** Dla API, raportów i granic między warstwami o odmiennych potrzebach. Nie kopiuj
automatycznie wszystkich pól encji, jeśli celem było ograniczenie i stabilizacja kontraktu.

**Koszt i pułapka:** DTO wymaga mapowania i kompatybilnej ewolucji; nie powinien niechcący ujawniać danych
innych tenantów.

**Rozwinięcie i ślad wykonania:** DTO powinien mieć stabilne znaczenie i jawne jednostki. Zwrócenie encji z
lazy getterem może uruchamiać kolejne SQL już podczas serializacji albo ujawnić pole, którego API nigdy nie
zamierzało publikować. Testuj dokładny publiczny kształt odpowiedzi i brak danych innych tenantów.

**Ćwiczenie sprawdzające:** Zdefiniuj publiczny summary i wewnętrzny widok administracyjny.

## Optimistic Offline Lock / Compare-and-set

**Intuicja:** Dwa żądania mogą obliczyć nową wartość na podstawie tego samego starego stanu. Optimistic Lock
nie blokuje odczytów z góry; sprawdza przy zapisie, czy wersja nadal jest tą, na której podjęto decyzję. Jeśli
nie, konflikt wymaga jawnego rozstrzygnięcia zamiast nadpisania cudzej zmiany.

**Problem i mechanizm:** Zapis wymaga zgodności wersji odczytanej wcześniej, co wykrywa utraconą aktualizację.

**Role i współpraca:** Wersja należy do rekordu, klient przechowuje expectedVersion, store porównuje i
zapisuje atomowo. Zero zmienionych wierszy oznacza, że warunek zapisu nie był spełniony.

**Przykład:** UPDATE counter SET value=2, version=8 WHERE id=1 AND version=7; zero wierszy oznacza konflikt.
Kod demonstruje ten kontrakt na snapshotcie.

**Mechanizm w kodzie:** Szablon SQL: wymaga tabel, parametrów i transakcji właściwych dla używanego silnika.
Fragment ilustruje mechanizm; nie jest automatycznie wykonywany przez kontrolę dokumentacji.

```sql
UPDATE inventory
SET available = :new_available,
    version = version + 1
WHERE sku = :sku AND version = :expected_version;
-- Sprawdź liczbę zmienionych wierszy: 0 oznacza konflikt lub brak.
```

**Konkretny przypadek użycia:** Dwóch edytujących zapas

- **Wejście:** Obaj odczytują available=10 i version=7.
- **Przebieg:** Pierwszy zapis aktualizuje wersję, drugi zapis warunkowy z 7 zmienia zero wierszy.
- **Oczekiwany wynik:** Cudza aktualizacja nie jest nadpisana starym wynikiem.
- **Błąd lub granica:** Retry wymaga ponownego obliczenia, nie tylko podmiany numeru wersji.

**Kiedy zastosować:** Gdy konflikty są na tyle rzadkie, że ich wykrywanie opłaca się bardziej niż ciągłe
blokowanie. Retry musi ponownie obliczyć decyzję na świeżym stanie.

**Koszt i pułapka:** Retry wymaga ponownego odczytu i obliczenia decyzji; ślepe ponowienie starego zapisu
utrwala błąd.

**Rozwinięcie i ślad wykonania:** Dwa odczyty wersji 0 prowadzą do dwóch konkurujących decyzji. Pierwszy zapis
przechodzi i podnosi wersję do 1, drugi nie spełnia warunku. Retry powinno jeszcze raz odczytać stan i
zastosować regułę; bez tego stara decyzja może naruszyć aktualny limit mimo nowego numeru wersji.

**Ćwiczenie sprawdzające:** Odczytaj dwukrotnie wersję 0, zapisz pierwszą zmianę i odrzuć drugą.

## Pessimistic Offline Lock / Lock before Change

**Intuicja:** Jeśli konflikt jest częsty albo decyzja wymaga utrzymania wyłączności, można zająć zasób przed
zmianą. Pessimistic Lock daje prawo pracy pod blokadą, lecz inne operacje muszą zaczekać lub dostać timeout.
Wariant offline może używać osobnego, dłużej żyjącego locka aplikacyjnego, co ma inny lifecycle niż transakcja
DB.

**Problem i mechanizm:** Zmiana najpierw uzyskuje wyłączność na zasobie albo jawne prawo jego edycji.

**Role i współpraca:** Transakcja pozyskuje blokadę, odczytuje chroniony stan i wykonuje zapis przed jej
zwolnieniem. Kolejność zajmowania wielu zasobów wpływa na możliwość deadlock.

**Przykład:** Transakcja SELECT ... FOR UPDATE blokuje wiersz zapasu przed dekrementacją; offline edit lock
może mieć osobny lease.

**Mechanizm w kodzie:** Szablon SQL: wymaga tabel, parametrów i transakcji właściwych dla używanego silnika.
Fragment ilustruje mechanizm; nie jest automatycznie wykonywany przez kontrolę dokumentacji.

```sql
BEGIN;
SELECT available FROM inventory WHERE sku = :sku FOR UPDATE;
UPDATE inventory SET available = available - :quantity
WHERE sku = :sku AND available >= :quantity;
COMMIT;
-- Kod sprawdza wynik UPDATE i obsługuje timeout blokady.
```

**Konkretny przypadek użycia:** Rezerwacja często konfliktującego SKU

- **Wejście:** Wiele requestów dotyczy ostatniej sztuki.
- **Przebieg:** Transakcja zajmuje wiersz, ocenia stan i dekrementuje go przed zwolnieniem.
- **Oczekiwany wynik:** Tylko legalna liczba rezerwacji przechodzi w tej granicy.
- **Błąd lub granica:** Długie HTTP pod blokadą zwiększa contention; wiele locków wymaga stałej kolejności.

**Kiedy zastosować:** Gdy chcesz ograniczyć koszt powtarzanych konfliktów. Nie trzymaj blokady bazodanowej
przez wolne zewnętrzne HTTP, jeśli można zmniejszyć zakres albo użyć rezerwacji.

**Koszt i pułapka:** Blokada bazodanowa i wielominutowy lock aplikacyjny to różne zakresy; deadlock i timeout
wymagają obsługi.

**Rozwinięcie i ślad wykonania:** SELECT FOR UPDATE należy wykonać przed decyzją i utrzymać blokadę do końca
transakcji. Nie obejmuj jej wolnym wywołaniem HTTP, jeśli możesz uniknąć długiej rezerwacji połączenia i
wiersza. Dla dwóch zasobów stosuj stałą kolejność oraz ograniczony czas oczekiwania, żeby kontrolować deadlock
i przeciążenie.

**Ćwiczenie sprawdzające:** Zaprojektuj kolejność blokowania dwóch kont i ogranicz czas transakcji.

## Cache-aside / Lazy Caching

**Intuicja:** Cache jest kopią przyspieszającą odczyt, a baza źródłem prawdy. W Cache-aside to aplikacja
decyduje: sprawdź kopię, przy braku pobierz źródło i zapamiętaj wynik. Po zmianie źródła musi także
rozstrzygnąć unieważnienie lub okres tolerowania starej wartości.

**Problem i mechanizm:** Aplikacja sprawdza cache, przy miss pobiera źródło prawdy i zapełnia kopię.

**Role i współpraca:** Klient zna cache i source, klucz identyfikuje zakres danych, loader wykonuje odczyt.
Miss uruchamia zasilenie kopii; hit pomija droższe źródło.

**Przykład:** CacheAside.get(p1) raz ładuje old; po zmianie źródła nadal daje old aż do invalidate(p1).

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
Product product = cache.get(key);
if (product == null) {
    product = products.load(key);
    cache.put(key, product, ttl);
}
return product;
```

**Konkretny przypadek użycia:** Cache ceny karty produktu

- **Wejście:** Pierwszy load zwraca old, źródło zmienia się na new.
- **Przebieg:** Hit oddaje kopię aż do invalidate lub expiry.
- **Oczekiwany wynik:** Kolejny load po unieważnieniu odczytuje new.
- **Błąd lub granica:** Nie wolno użyć starej ceny jako jedynego źródła krytycznej decyzji checkout.

**Kiedy zastosować:** Dla powtarzanych odczytów z akceptowalną starością danych. Przy ścisłym niezmienniku
zapisu nie podejmuj decyzji wyłącznie na podstawie potencjalnie starego cache.

**Koszt i pułapka:** Nie ma atomowości DB plus cache; wyścig miss ze zmianą może ponownie zapisać starą
wartość. Model nie ma TTL ani współbieżności.

**Rozwinięcie i ślad wykonania:** Model podstawowy pokazuje stale value do invalidate. Rozbudowany
BoundedTtlCache dodatkowo ogranicza czas ważności i liczbę wpisów, ale nadal ma jednego właściciela stanu.
Przy współbieżnej bazie i cache przeanalizuj osobno przeplot miss oraz zapisu: lokalne LRU nie rozwiązuje tej
niespójności.

**Ćwiczenie sprawdzające:** Odtwórz przeplot odczyt starej DB → commit nowej → invalidate → zapis starego
cache.

## Read-through Cache

**Intuicja:** Wiele klientów może powtarzać identyczną obsługę miss. Read-through przenosi ją do samej warstwy
cache, tak aby klient korzystał z jednego get. Różnica względem Cache-aside dotyczy właściciela loadera i
kontraktu dostępu, nie magicznie silniejszej spójności kopii.

**Problem i mechanizm:** Cache sam deleguje odczyt brakującej wartości do skonfigurowanego loadera.

**Role i współpraca:** Cache wywołuje skonfigurowany loader, store dostarcza dane, klient widzi uzgodniony
interfejs. Wyjątek źródła nadal musi być odróżniony od poprawnego braku danych.

**Przykład:** Aplikacja wywołuje products.get(id), a warstwa cache wywołuje bazę tylko na miss.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
ProductCache cache = new ProductCache(
        key -> products.load(key),
        ttl,
        capacity);
Product product = cache.get(productId); // Loader jest wewnątrz cache.
```

**Konkretny przypadek użycia:** Wspólny loader produktów

- **Wejście:** Pięciu klientów potrzebuje identycznej obsługi miss.
- **Przebieg:** Read-through Cache sam wywołuje uzgodniony loader.
- **Oczekiwany wynik:** Klient korzysta z jednego get z opisanym kosztem I/O.
- **Błąd lub granica:** Awaria loadera nie może być bez rozróżnienia zapamiętana jako brak produktu.

**Kiedy zastosować:** Gdy chcesz ujednolicić sposób zasilania kopii. Dokumentuj, że get może wykonywać I/O i
jak jest ograniczone czasowo.

**Koszt i pułapka:** Różni się odpowiedzialnością loadera od cache-aside; awaria loadera i negatywny cache
wymagają polityki.

**Rozwinięcie i ślad wykonania:** Klient wywołuje tylko cache.get, a loader jest odpowiedzialnością warstwy
cache. To upraszcza użycie, ale ukrywa I/O, jeśli kontrakt get wygląda jak zwykły HashMap. Określ timeout
loadera i sposób rozróżnienia „brak produktu” od „źródło nie odpowiedziało”.

**Ćwiczenie sprawdzające:** Zdecyduj, czy brak produktu można cacheować i na jak długo.

## Write-through Cache

**Intuicja:** Jeśli część kodu zapisuje bazę, a część tylko cache, kopie szybko się rozchodzą. Write-through
prowadzi zapis przez jedną warstwę, która synchronicznie kontaktuje się ze store przed uznaniem operacji za
udaną zgodnie z własnym kontraktem. Klient nie odkłada trwałości na nieznaną przyszłość.

**Problem i mechanizm:** Zapis przechodzi przez cache, który synchronicznie aktualizuje store przed sukcesem
dla klienta.

**Role i współpraca:** Writer cache pośredniczy, store potwierdza zapis, kopia jest aktualizowana lub
unieważniana. Dokładne okna awarii zależą od kolejności i mechanizmu recovery implementacji.

**Przykład:** put(product) czeka na potwierdzenie zapisu bazy i aktualizuje kopię według kontraktu dostawcy.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
void put(Key key, Value value) {
    store.save(key, value); // Czekamy na wynik źródła.
    cache.putLocal(key, value);
}
// Awaria między krokami wymaga ustalonej polityki naprawy kopii.
```

**Konkretny przypadek użycia:** Synchroniczny zapis przez cache

- **Wejście:** Klient zmienia nazwę produktu p1.
- **Przebieg:** Warstwa zapisuje store przed uznaniem operacji za sukces i aktualizuje kopię.
- **Oczekiwany wynik:** Jest wspólna ścieżka zapisu dla klientów.
- **Błąd lub granica:** Crash po DB przed zmianą kopii nadal wymaga polityki naprawy.

**Kiedy zastosować:** Gdy wspólne wejście zapisu upraszcza utrzymanie kopii i dopuszczasz koszt oczekiwania.
Sama nazwa nie zapewnia atomowej transakcji między dowolnym cache i bazą.

**Koszt i pułapka:** Wyższa latencja; nazwa nie gwarantuje atomowości wszystkich awarii. Trzeba sprawdzić
kolejność i tryb recovery implementacji.

**Rozwinięcie i ślad wykonania:** Zapis jest potwierdzany dopiero zgodnie z kontraktem przejścia przez store.
Jeśli proces pada po commicie bazy, lecz przed aktualizacją cache, odczyt może pozostać stary. Sama etykieta
write-through nie rozstrzyga tego okna; potrzebny jest opis recovery konkretnej implementacji.

**Ćwiczenie sprawdzające:** Przeanalizuj crash między zapisem store a odświeżeniem cache.

## Write-behind / Write-back Cache

**Intuicja:** Wymóg małego latency zapisu może prowadzić do przyjęcia zmiany do bufora i późniejszego zapisu
paczką. Write-behind przesuwa koszt źródła poza krytyczną ścieżkę klienta. W zamian trzeba ustalić, co oznacza
potwierdzenie oraz ile danych może zginąć przy utracie bufora.

**Problem i mechanizm:** Szybki zapis do cache jest później asynchronicznie przenoszony do źródła trwałego.

**Role i współpraca:** Bufor przyjmuje i wersjonuje zmiany, worker opróżnia je do store, checkpoint potwierdza
postęp. Retry starszej porcji nie może nadpisać późniejszej wartości.

**Przykład:** Zmiany liczników są buforowane i zapisywane paczkami co sekundę.

**Mechanizm w kodzie:** Pseudokod: nazwy oznaczają role i operacje z opisu. To schemat algorytmu lub
interakcji, bez deklaracji implementacji infrastruktury.

```text
put(key, value, version) -> zaakceptuj do określonego bufora
worker:
    pobierz porcję w kolejności wersji
    zapisz do store idempotentnie
    potwierdź postęp dopiero po trwałym zapisie
```

**Konkretny przypadek użycia:** Buforowane liczniki odsłon

- **Wejście:** Odsłony napływają szybko, store zapisuje paczkę co sekundę.
- **Przebieg:** Write-behind przyjmuje wersjonowane zmiany i opróżnia bufor.
- **Oczekiwany wynik:** Krytyczna ścieżka może być krótka przy określonym opóźnieniu trwałości.
- **Błąd lub granica:** Utrata nietrwałego bufora może utracić potwierdzone dane; retry nie może nadpisać
  nowszej wersji.

**Kiedy zastosować:** Dla danych o jasno określonej tolerancji utraty i opóźnienia. Dla krytycznych zapisów
finansowych nietrwały bufor zwykle nie spełni wymaganego kontraktu potwierdzenia.

**Koszt i pułapka:** Potwierdzone dane mogą zginąć przy utracie nietrwałego bufora; kolejność i retry muszą
zachować semantykę.

**Rozwinięcie i ślad wykonania:** Sukces dla klienta może poprzedzać zapis docelowego store, więc kluczowe
jest, czy bufor sam jest trwały. Przy dwóch zmianach jednego klucza zachowaj ich wersje albo kolejność. Retry
starszego batcha nie powinno nadpisać nowszej wartości po udanym zapisie kolejnego batcha.

**Ćwiczenie sprawdzające:** Określ RPO i pokaż konflikt dwóch aktualizacji tego samego klucza.

## Refresh-ahead / Stale-while-revalidate

**Intuicja:** Popularna kopia nie musi znikać nagle, zmuszając każdego klienta do kosztownego miss.
Refresh-ahead odnawia ją przed wygaśnięciem, a Stale-while-revalidate może podać dopuszczalnie starą wartość i
zlecić odnowienie w tle. To pokrewne, ale odmienne decyzje o momencie uruchomienia refresh.

**Problem i mechanizm:** Odnawianie danych odbywa się przed wygaśnięciem lub w tle przy podaniu dopuszczalnie
starej kopii.

**Role i współpraca:** Cache przechowuje wartość i wiek, polityka wyznacza granice świeżości, jeden
odświeżający ładuje nowy stan. Klient powinien znać dopuszczalny rodzaj wyniku.

**Przykład:** Katalog zwraca wersję sprzed 30 sekund i jeden worker odświeża ją w tle; cena checkoutu idzie do
źródła prawdy.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
Entry entry = cache.get(key);
if (entry.isFresh(now)) return entry.value();
if (entry.isWithinStaleLimit(now)) {
    refreshOnceInBackground(key);
    return entry.value();
}
return loadSynchronously(key, deadline);
```

**Konkretny przypadek użycia:** Popularny katalog przy expiry

- **Wejście:** Tysiące czytelników trafia w wygasającą kartę produktu.
- **Przebieg:** Jeden refresh odnawia kopię, klient może dostać stale w dozwolonym limicie.
- **Oczekiwany wynik:** Odczyty nie wywołują lawiny identycznych odnowień.
- **Błąd lub granica:** Starość ma maksymalną granicę; błąd refresh nie daje prawa do nieskończonego
  przedłużania.

**Kiedy zastosować:** Dla odczytów tolerujących starą kopię i wymagających niskiego latency. Ustal maksymalną
starość, po której nie wolno dalej udawać użytecznych danych.

**Koszt i pułapka:** Nie nadaje się dla wszystkich decyzji; trzeba ustalić maksymalną starość, single-flight i
awarię odświeżania.

**Rozwinięcie i ślad wykonania:** Freshness i availability są dwiema osiami decyzji. Stara karta produktu może
być akceptowalna, ale limit rezerwacji już nie. Ustal wiek maksymalny, tylko jednego odświeżającego i
zachowanie po błędzie; samo przedłużenie TTL może bez końca utrzymywać nieprawidłową kopię.

**Ćwiczenie sprawdzające:** Rozdziel pola, które mogą być stare, od pól wymagających walidacji przy zapisie.

## Materialized View / Denormalized Read Model

**Intuicja:** Złożony ekran może stale wykonywać te same JOIN i sumy. Materialized View zapisuje wcześniej
przygotowany wynik, dzięki czemu odczyt jest prosty. Koszt obliczeń przenosi na aktualizację albo odświeżanie
kopii, więc musi istnieć źródło umożliwiające jej kontrolę i odbudowę.

**Problem i mechanizm:** Wynik kosztownego złączenia lub agregacji utrzymuje się jako osobny widok do odczytu.

**Role i współpraca:** Źródło posiada fakty, projekcja utrzymuje wynik, czytelnik używa formy dobranej do
zapytania. Aktualizacja może być synchroniczna lub opóźniona według wymagań.

**Przykład:** OrderSummary łączy status i sumę pozycji, aktualizując się z eventów.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
void on(OrderPaid event) {
    projection.applyOnce(event.id(), summary ->
            summary.markPaid(event.orderId(), event.amount()));
}
OrderSummary result = projection.find(orderId);
```

**Konkretny przypadek użycia:** Widok listy sprzedaży

- **Wejście:** Raport potrzebuje sumy i statusu zamiast kosztownego JOIN przy każdym odczycie.
- **Przebieg:** Projekcja aktualizuje zdenormalizowany OrderSummary.
- **Oczekiwany wynik:** Odczyt jest prosty i ma opisany lag.
- **Błąd lub granica:** Replay nie może ponownie wysłać maila; widok musi być odbudowywalny ze źródła.

**Kiedy zastosować:** Dla częstych, kosztownych odczytów o znanym kształcie. Zdefiniuj lag i rebuild; szybki
odczyt nie uzasadnia braku właściciela poprawności kopii.

**Koszt i pułapka:** Utrzymanie, odbudowa, deduplikacja i lag są kosztem szybkiego odczytu.

**Rozwinięcie i ślad wykonania:** Projekcja jest odbudowywalnym wynikiem, więc źródło prawdy musi zawierać
wszystko potrzebne do jej odtworzenia. Test zaczyna od pustej projekcji i porównuje ją z wcześniej
aktualizowaną. Nie dodawaj nieodwracalnych efektów, np. maila, do funkcji apply używanej podczas replay.

**Ćwiczenie sprawdzające:** Odbuduj widok z historii i porównaj wynik z pełną agregacją źródła.

## Keyset / Seek Pagination

**Intuicja:** Duży OFFSET każe bazie ominąć wiele wcześniejszych rekordów. Keyset zaczyna od ostatniego
znanego klucza, wykorzystując stabilny porządek i odpowiedni indeks. Klucz musi rozróżniać remisy: sam czas
utworzenia nie wystarczy, jeśli kilka wierszy ma tę samą wartość.

**Problem i mechanizm:** Kolejna strona zaczyna się za stabilnym złożonym kluczem ostatniego elementu.

**Role i współpraca:** Kursor przechowuje ostatnią parę sortowania, zapytanie wybiera wartości większe, LIMIT
wyznacza rozmiar strony. Indeks odpowiada porządkowi WHERE i ORDER BY.

**Przykład:** WHERE (created_at,id) > (:time,:id) ORDER BY created_at,id LIMIT 50 omija koszt dużego OFFSET.

**Mechanizm w kodzie:** Szablon SQL: wymaga tabel, parametrów i transakcji właściwych dla używanego silnika.
Fragment ilustruje mechanizm; nie jest automatycznie wykonywany przez kontrolę dokumentacji.

```sql
SELECT id, created_at, status
FROM orders
WHERE created_at > :time
   OR (created_at = :time AND id > :id)
ORDER BY created_at, id
LIMIT :page_size;
```

**Konkretny przypadek użycia:** Strona z remisami czasu

- **Wejście:** Dwa rekordy mają created_at 12:00, ID 101 i 102.
- **Przebieg:** Cursor zawiera czas i ID; następny SELECT zaczyna ściśle za ostatnią parą.
- **Oczekiwany wynik:** Rekord 102 nie jest pominięty przez porównanie samego czasu.
- **Błąd lub granica:** Zmiana sortowanego pola nadal wymaga semantyki odczytu zmiennego zbioru.

**Kiedy zastosować:** Dla przewijania dużych zbiorów po ustalonym porządku. Losowy skok do strony 500 ma inne
potrzeby; keyset nie gwarantuje również snapshotu stale zmieniającej się kolekcji.

**Koszt i pułapka:** Potrzebny jest pełny porządek i indeks; zmiana sortowanego pola może przenosić elementy
między stronami.

**Rozwinięcie i ślad wykonania:** Kursor zawiera ostatnią parę created_at i id, ponieważ sam czas nie daje
unikalnego porządku. Kolejny SELECT zaczyna się ściśle za tą parą. Przy rosnącym zbiorze nie otrzymujesz
automatycznie snapshotu: określ, czy nowe rekordy i zmiany klucza sortowania mogą pojawić się w kolejnych
stronach.

**Ćwiczenie sprawdzające:** Użyj dwóch rekordów z tym samym czasem i sprawdź brak pominięcia po ID.

## Expand–Migrate–Contract / Parallel Change

**Intuicja:** Podczas wdrożenia nowa i stara wersja mogą jednocześnie korzystać z danych. Usunięcie lub zmiana
kolumny jednym krokiem może od razu zepsuć starsze instancje. Expand–Migrate–Contract rozdziela dodanie
zgodnej reprezentacji, przeniesienie danych i dopiero późniejsze usunięcie starego kontraktu.

**Problem i mechanizm:** Schemat i kontrakt ewoluują etapami, by stare i nowe wersje mogły współistnieć.

**Role i współpraca:** Migracja expand dodaje możliwość, aplikacje obsługują okres przejściowy, backfill
uzupełnia historię, contract usuwa nieużywany format po wycofaniu klientów.

**Przykład:** Dodaj nullable full_name, wdroż zgodne zapisy, wykonaj wznawialny backfill, przełącz odczyt,
dopiero usuń stare pola.

**Mechanizm w kodzie:** Szablon SQL: wymaga tabel, parametrów i transakcji właściwych dla używanego silnika.
Fragment ilustruje mechanizm; nie jest automatycznie wykonywany przez kontrolę dokumentacji.

```sql
-- Etap expand; starszy kod nadal działa.
ALTER TABLE customers ADD COLUMN full_name VARCHAR(200);
-- Kolejne etapy: zgodny zapis, wznawialny backfill, przełączenie odczytu.
-- DROP starej kolumny następuje dopiero po wycofaniu jej klientów.
```

**Konkretny przypadek użycia:** Migracja nazwy klienta

- **Wejście:** Stara wersja używa first/last_name, nowa full_name.
- **Przebieg:** Expand dodaje pole, zgodny zapis i backfill poprzedzają przełączenie oraz contract.
- **Oczekiwany wynik:** Stare i nowe instancje współistnieją bez nagłego braku kolumny.
- **Błąd lub granica:** Backfill nie może nadpisać nowszej edycji, a rollback kodu nie odwraca danych.

**Kiedy zastosować:** Dla zmian schematu i kontraktów wymagających zgodnego rollout. Kontroluj nowe zapisy
podczas backfillu; samo skopiowanie istniejących rekordów nie kończy migracji.

**Koszt i pułapka:** Dual write wymaga spójnej granicy transakcji; migracja i rollback danych mają inny koszt
niż rollback kodu.

**Rozwinięcie i ślad wykonania:** Etap expand umożliwia działanie starego i nowego kodu na tym samym
schemacie. Backfill musi być wznawialny i nie może nadpisywać nowszych aktualizacji. Dopiero po wycofaniu
starego kodu oraz obserwacji ruchu wolno wykonać contract; rollback po usunięciu kolumn może wymagać
odtworzenia danych.

**Ćwiczenie sprawdzające:** Zaprojektuj mieszany ruch dwóch wersji podczas backfillu.
