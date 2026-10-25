# Mapowanie obiektowo-relacyjne i stan sesji

[Moduł](../../README.md) · [Indeks](../../INDEKS.md) · [Materiały](../../MATERIALY.md#poeaa)

Wzorce uzupełniające enterprise. **14 kart.**

**Status przykładów:** moduł dokumentacyjny. Fragmenty Java, SQL i HTTP oraz pseudokod są objaśnieniami
mechanizmu; kompletne modele i scenariusze poprawności również znajdują się w Markdown. Nie ma osobnej
aplikacji, buildu Java ani testów uruchamianych przez Maven.

**Rozbudowany kod i omówienie:**
[Identity Map, mapper i zapis Unit of Work](../../labs/01-domain-and-persistence.md). Studium w Markdown
rozwija wskazany podzbiór wzorców tej kategorii.

**Jak rozumieć tę kategorię:** Mapowanie tłumaczy dwa różne modele: obiekty z referencjami i zachowaniem oraz
rekordy z kluczami i ograniczeniami. Pytaj, gdzie znajduje się tożsamość, kto zapisuje relację i jak odtwarza
się całość. Osobna grupa dotyczy stanu sesji i granic API. Fragmenty SQL pokazują ideę schematu; konkretne
typy, indeksy i gwarancje trzeba dopasować do silnika używanego w danym projekcie.

## Spis kart

- [Foreign Key Mapping / Association Table Mapping](#foreign-key-mapping--association-table-mapping)
- [Dependent Mapping / Embedded Value](#dependent-mapping--embedded-value)
-
  [Single Table / Class Table / Concrete Table Inheritance](#single-table--class-table--concrete-table-inheritance)
- [Serialized LOB](#serialized-lob)
- [Identity Field / Surrogate Key / Natural Key](#identity-field--surrogate-key--natural-key)
- [Metadata Mapping / Reflection Mapper](#metadata-mapping--reflection-mapper)
- [Query Object / Query Specification](#query-object--query-specification)
- [Association Ownership / Optimized Collection Update](#association-ownership--optimized-collection-update)
- [Coarse-grained Lock / Implicit Lock](#coarse-grained-lock--implicit-lock)
-
  [Client Session State / Server Session State / Database Session State](#client-session-state--server-session-state--database-session-state)
- [Remote Facade / Coarse-grained API](#remote-facade--coarse-grained-api)
- [Separated Interface / Layer Supertype](#separated-interface--layer-supertype)
- [Registry / Service Locator](#registry--service-locator)
- [Special Case](#special-case)

## Foreign Key Mapping / Association Table Mapping

**Intuicja:** Referencja obiektu w pamięci nie może zostać zapisana jako adres do przyszłego odczytu po
restarcie. Relację trzeba reprezentować kluczem lub tabelą powiązań. Foreign Key Mapping wiąże rekord z
właścicielem, Association Table przechowuje relację niezależnie, często dla wielu–do–wielu.

**Problem i mechanizm:** Relację obiektów zapisuje się przez klucz obcy albo osobną tabelę powiązań, zgodnie z
licznością i właścicielem relacji.

**Role i współpraca:** Klucz główny identyfikuje stronę relacji, obcy wskazuje drugi rekord, ograniczenia
chronią istnienie i unikalność powiązania. Mapper odbudowuje referencje zgodnie z zakresem modelu.

**Przykład:** OrderLine ma order_id; relacja Product–Tag ma tabelę product_tag z unikalnością pary kluczy.

**Mechanizm w kodzie:** Szablon SQL: wymaga tabel, parametrów i transakcji właściwych dla używanego silnika.
Fragment ilustruje mechanizm; nie jest automatycznie wykonywany przez kontrolę dokumentacji.

```sql
CREATE TABLE product_tag (
    product_id BIGINT NOT NULL REFERENCES products(id),
    tag_id BIGINT NOT NULL REFERENCES tags(id),
    PRIMARY KEY (product_id, tag_id)
);
```

**Konkretny przypadek użycia:** Produkty przypisane do wielu kolekcji

- **Wejście:** Produkt P należy do kolekcji A i B, każda ma własną kolejność produktów.
- **Przebieg:** Mapowanie wiele-do-wielu zapisuje relację w tabeli z kluczem pary i kolumną pozycji.
- **Oczekiwany wynik:** Usunięcie P z A nie usuwa produktu ani powiązania z B.
- **Błąd lub granica:** Brak unikalności pary dopuszcza duplikaty; kaskadowe usuwanie produktu z relacji może
  skasować wspólny obiekt.

**Kiedy zastosować:** Gdy projektujesz utrwalenie relacji obiektów. Liczność i lifecycle są ważniejsze niż
mechaniczne tłumaczenie każdej listy na dowolną tabelę.

**Koszt i pułapka:** Mapowanie kolekcji wymaga kontroli duplikatów i kosztu synchronizacji; relacja
dwukierunkowa ma dwa widoki jednego faktu.

**Rozwinięcie i ślad wykonania:** Klucz obcy przechowuje właściciela relacji po stronie wielu, a tabela
asocjacyjna pozwala modelować wiele–do–wielu. Unikalność pary i klucze obce są częścią poprawności, nie tylko
optymalizacji. Przy obiekcie dwukierunkowym ustal jedną autorytatywną stronę zapisu, żeby oba widoki nie
rozjechały się w pamięci.

**Ćwiczenie sprawdzające:** Zapisz zmianę tagów bez usuwania i odtwarzania wszystkich powiązań.

## Dependent Mapping / Embedded Value

**Intuicja:** Adres lub Money może należeć do właściciela bez własnego niezależnego lifecycle. Embedded Value
spłaszcza jego pola do rekordu właściciela, Dependent Mapping pozwala właścicielowi zapisywać obiekt zależny.
To pokrewne decyzje o mapowaniu, nie stwierdzenie, że każdy zależny obiekt musi być pozbawiony ID.

**Problem i mechanizm:** Obiekt zależny jest utrwalany przez właściciela albo jego wartość trafia do kolumn
rekordu właściciela.

**Role i współpraca:** Order posiada wartość total, mapper zapisuje cents i currency. Pozycja może mieć lineId
wewnątrz agregatu, choć nie ma osobnego repozytorium publicznego.

**Przykład:** Money staje się amount_cents i currency w orders, a zależna pozycja zamówienia nie ma osobnego
repozytorium.

**Mechanizm w kodzie:** Szablon SQL: wymaga tabel, parametrów i transakcji właściwych dla używanego silnika.
Fragment ilustruje mechanizm; nie jest automatycznie wykonywany przez kontrolę dokumentacji.

```sql
CREATE TABLE orders (
    id BIGINT PRIMARY KEY,
    total_cents BIGINT NOT NULL,
    total_currency CHAR(3) NOT NULL
);
-- Dwie kolumny reprezentują jedną wartość Money.
```

**Konkretny przypadek użycia:** Adres dostawy zamówienia

- **Wejście:** Order42 zawiera adres bez samodzielnego cyklu życia.
- **Przebieg:** Embedded zapisuje pola adresu przy zamówieniu albo dependent w tabeli zależnej od jego klucza.
- **Oczekiwany wynik:** Zmiana adresu dostawy jest częścią zmiany agregatu i nie wymaga repozytorium adresów.
- **Błąd lub granica:** Współdzielony adres klienta aktualizowany globalnie miałby inny kontrakt niż
  historyczny snapshot dostawy.

**Kiedy zastosować:** Gdy składnik ma żyć i zmieniać się wraz z właścicielem. Nie współdziel go mutowalnie
między właścicielami bez jasnej semantyki.

**Koszt i pułapka:** Embedded Value spłaszcza dane; zmiana schematu wpływa na właściciela. Obiekt zależny nie
jest automatycznie beztożsamościowy.

**Rozwinięcie i ślad wykonania:** Embedded Money trafia do dwóch kolumn właściciela i nie wymaga własnego
repozytorium. Obiekt zależny może jednak mieć tożsamość wewnątrz agregatu, np. lineId. Nie utożsamiaj braku
osobnego lifecycle z całkowitym brakiem identyfikatora potrzebnego do aktualizacji elementu.

**Ćwiczenie sprawdzające:** Rozdziel Address jako wartość od AddressBookEntry jako encji z ID.

## Single Table / Class Table / Concrete Table Inheritance

**Intuicja:** Dziedziczenie klas nie ma jednego oczywistego odpowiednika w relacyjnej bazie. Single Table
zbiera warianty w jednej tabeli, Class Table dzieli pola po poziomach hierarchii, Concrete Table powiela
wspólne pola w tabelach konkretnych typów. Wybór zmienia koszt zapisu i zapytań po całej rodzinie.

**Problem i mechanizm:** Hierarchię obiektów mapuje się na wspólną tabelę, tabele po klasie albo osobne tabele
typów konkretnych.

**Role i współpraca:** Discriminator lub układ kluczy identyfikuje wariant, mapper odtwarza podtyp. JOIN albo
UNION jest skutkiem wybranego schematu, nie przypadkowym detalem ORM.

**Przykład:** PaymentCard i BankTransfer mają jedną payments z discriminator, albo osobne card_payments i
bank_transfers.

**Mechanizm w kodzie:** Pseudokod: nazwy oznaczają role i operacje z opisu. To schemat algorytmu lub
interakcji, bez deklaracji implementacji infrastruktury.

```text
Single Table:
    payments(id, type, amount, card_token?, bank_account?)
Class Table:
    payments(id, amount), card_payments(id FK, card_token)
Concrete Table:
    cards(id, amount, card_token), transfers(id, amount, bank_account)
```

**Konkretny przypadek użycia:** Mapowanie kilku metod płatności

- **Wejście:** Payment ma wspólną kwotę, CardPayment ma token, TransferPayment ma numer przelewu.
- **Przebieg:** Single table używa dyskryminatora, class table łączy bazę z podtypem, concrete table powiela
  wspólne kolumny.
- **Oczekiwany wynik:** Wybrany wariant świadomie optymalizuje główne zapytanie i ograniczenia dla podtypów.
- **Błąd lub granica:** Jedna tabela ma pola opcjonalne, joiny kosztują odczyt, a osobne tabele utrudniają
  wspólne klucze i zapytania.

**Kiedy zastosować:** Gdy model ma rzeczywistą hierarchię utrwalaną relacyjnie. Czasem kompozycja lub jawny
typ wariantu upraszcza projekt bardziej niż dziedziczenie encji.

**Koszt i pułapka:** Jedna tabela ma puste kolumny, class table wymaga JOIN, concrete table utrudnia wspólne
zapytania i unikalność ID.

**Rozwinięcie i ślad wykonania:** Porównaj nie tylko zapis jednego konkretnego typu, lecz również zapytanie po
całej hierarchii. Single Table wybiera discriminator, Class Table składa JOIN, Concrete Table może wymagać
UNION. Wspólne ograniczenia i indeksy powinny zostać zaprojektowane osobno dla każdej reprezentacji.

**Ćwiczenie sprawdzające:** Narysuj schemat wszystkich trzech wariantów i zapytanie o ostatnie 10 płatności.

## Serialized LOB

**Intuicja:** Część złożonej konfiguracji może być używana zawsze jako całość i nie wymagać relacyjnych
zapytań po każdym polu. Serialized LOB zapisuje ją w jednej reprezentacji, np. JSON. Upraszcza mapowanie, ale
przenosi część schematu oraz jego ewolucji do kodu odczytującego dokument.

**Problem i mechanizm:** Złożona wartość jest przechowywana jako jedna serializowana kolumna zamiast osobnego
modelu relacyjnego.

**Role i współpraca:** Właściciel przechowuje payload, wersja opisuje format, dekoder waliduje oraz migruje
starszą postać. Wybrane pola potrzebne do indeksowania mogą wymagać wydzielenia.

**Przykład:** Snapshot ustawień raportu trafia do JSON z version=2, a wyszukiwane pola są wydzielone do
indeksowanych kolumn.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
record StoredReport(int schemaVersion, String settingsJson) { }
StoredReport stored = new StoredReport(2, json.encode(settings));
ReportSettings decoded = decoders.forVersion(stored.schemaVersion())
        .decode(stored.settingsJson());
```

**Konkretny przypadek użycia:** Rzadko odczytywane ustawienia raportu

- **Wejście:** Raport ma zagnieżdżony układ 40 kolumn, bez filtrowania po jego wnętrzu.
- **Przebieg:** Mapper zapisuje wersjonowany dokument w jednej kolumnie i odtwarza go przy odczycie całości.
- **Oczekiwany wynik:** Model układu nie wymaga kilkunastu tabel relacyjnych.
- **Błąd lub granica:** Zmiana formatu wymaga kompatybilnego odczytu; serializacja nie nadaje się do
  niebezpiecznej deserializacji obiektów z obcego źródła.

**Kiedy zastosować:** Dla snapshotów i złożonych wartości o ograniczonej potrzebie zapytań. Nie wybieraj blobu
tylko po to, by uniknąć zaprojektowania kontraktu danych.

**Koszt i pułapka:** Ukryty schemat wymaga migracji i limitów rozmiaru; natywna deserializacja niezaufanych
obiektów jest ryzykowna.

**Rozwinięcie i ślad wykonania:** Serializowany blob przechowuje całą wartość jako jednostkę zmiany. To
upraszcza zapis, lecz utrudnia filtrowanie i drobną aktualizację. Jawny format oraz version pozwalają odczytać
historyczny snapshot; dowolna deserializacja obiektu Javy nie powinna być kontraktem przyjmowania niezaufanych
danych.

**Ćwiczenie sprawdzające:** Odczytaj starą wersję dokumentu i zaprojektuj jawny upgrade danych.

## Identity Field / Surrogate Key / Natural Key

**Intuicja:** System potrzebuje stabilnie wskazywać rekord, nawet jeśli zmienia się jego nazwa lub email.
Identity Field łączy obiekt z kluczem persystencji. Surrogate Key jest technicznym ID, Natural Key wynika z
domeny; ten drugi wymaga pewności co do trwałości i zakresu unikalności znaczenia.

**Problem i mechanizm:** Obiekt przechowuje stabilny identyfikator relacyjny lub uzgodniony klucz naturalny.

**Role i współpraca:** Obiekt zachowuje ID, relacje używają go zamiast zmiennych atrybutów, baza egzekwuje
ograniczenia. Publiczny identyfikator może mieć inny kontrakt niż wewnętrzny klucz tabeli.

**Przykład:** Customer ma UUID, a email jest zmiennym unikalnym atrybutem; nie używa się go jako trwałego ID
relacji.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
record CustomerId(UUID value) { }
record CustomerSnapshot(CustomerId id, String email) { }

// Zmiana email zachowuje customerId w zamówieniach i historii.
```

**Konkretny przypadek użycia:** Tożsamość klienta po zmianie email

- **Wejście:** Klient ma id=42 i email a@example.test, później zmienia email.
- **Przebieg:** Relacje wskazują stabilny surrogate id, a email ma osobny warunek unikalności.
- **Oczekiwany wynik:** Zmiana email nie wymusza aktualizacji kluczy obcych wszystkich zamówień.
- **Błąd lub granica:** Klucz techniczny nie zastępuje unikalności biznesowej; dwa rekordy o tym samym email
  nadal trzeba rozstrzygnąć.

**Kiedy zastosować:** Gdy dane mają historię i powiązania. Rozróżnij tożsamość od atrybutu, który tylko dziś
wygląda na unikalny.

**Koszt i pułapka:** Losowe klucze mają koszty indeksów; sekwencje wymagają zakresu unikalności. Klucz
naturalny może zmienić znaczenie.

**Rozwinięcie i ślad wykonania:** Stałe ID pozwala zmienić email bez przepisywania kluczy powiązanych
rekordów. Unikalność email pozostaje odrębnym ograniczeniem i może mieć zakres tenanta. Nie wymagaj od klienta
wnioskowania o kolejności czasu z UUID, jeśli użyty wariant identyfikatora tego nie gwarantuje.

**Ćwiczenie sprawdzające:** Zaprojektuj zmianę email bez przepisywania wszystkich kluczy obcych.

## Metadata Mapping / Reflection Mapper

**Intuicja:** Ręczne mapowanie setek podobnych pól bywa powtarzalne. Metadata Mapping opisuje relacje
deklaratywnie, a infrastruktura wykorzystuje ten opis do odczytu i zapisu. Zyskujesz wspólny mechanizm, ale
błędne metadane i generowany SQL nadal muszą być widoczne oraz sprawdzane.

**Problem i mechanizm:** Powtarzalne mapowanie wynika z deklaratywnych metadanych zamiast ręcznego kodowania
każdej kolumny.

**Role i współpraca:** Deskryptor wskazuje typ, tabelę i pola, mapper interpretuje go, walidacja wykrywa brak
kolumny lub nieobsługiwany typ. Refleksja jest techniką, nie pełnym celem wzorca.

**Przykład:** Adnotacje lub deskryptor łączą pole currency z currency_code, a mapper generuje SQL zgodny z
kontraktem.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
Mapping<Customer> mapping = Mapping.forType(Customer.class)
        .table("customers")
        .id("id", "customer_id")
        .field("email", "email_address");
mapper.register(mapping);
```

**Konkretny przypadek użycia:** Mapper dla trzech podobnych encji

- **Wejście:** Encje mają jawne metadane kolumn i różne konstruktory.
- **Przebieg:** Mapper odczytuje metadane, buduje plan mapowania i jawnie obsługuje konwersje typów.
- **Oczekiwany wynik:** Powtarzalny kod przypisania kolumn nie jest kopiowany między adapterami.
- **Błąd lub granica:** Refleksyjne ustawienie pól może ominąć niezmienniki; błędne nazwy kolumn powinny być
  wykrywane wcześnie.

**Kiedy zastosować:** Gdy powtarzalność mapowania uzasadnia wspólną obsługę. Zachowaj testy round-trip i
możliwość oceny rzeczywistych zapytań.

**Koszt i pułapka:** Refleksja, błędy dopiero przy starcie i ukryte I/O zwiększają złożoność; metadane
wymagają walidacji.

**Rozwinięcie i ślad wykonania:** Deklaracja mapowania jest wejściem do infrastruktury, więc waliduj ją przy
starcie albo w teście adaptera. Ujawnienie literówki dopiero przy pierwszym rzadkim żądaniu podnosi koszt
błędu. Zachowaj możliwość obejrzenia wygenerowanego SQL, aby warstwa metadanych nie ukrywała kosztu odczytu.

**Ćwiczenie sprawdzające:** Wprowadź literówkę kolumny i określ najwcześniejszy etap wykrycia.

## Query Object / Query Specification

**Intuicja:** Dynamiczne filtry łatwo zamieniają się w konkatenowanie fragmentów SQL w kontrolerze. Query
Object reprezentuje potrzebne kryteria jako dane lub drzewo wyrażeń. Wykonawca tłumaczy je na parametryzowane
zapytanie, dzięki czemu klient opisuje zamiar, a szczegóły SQL są kontrolowane w jednym miejscu.

**Problem i mechanizm:** Zapytanie staje się obiektem lub drzewem wyrażeń możliwym do złożenia i bezpiecznego
wykonania.

**Role i współpraca:** Obiekt kryteriów przechowuje filtry, builder może je komponować, executor dobiera
dozwolone pola i parametry. Limit oraz porządek są częścią kontraktu kosztu.

**Przykład:** OrdersQuery(customerId, states, cursor) jest tłumaczony na parametryzowany SQL, bez konkatenacji
danych użytkownika.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
record OrdersQuery(
        CustomerId customer,
        Set<Status> statuses,
        Cursor after,
        int limit) { }
List<OrderSummary> page = queryExecutor.execute(criteria);
```

**Konkretny przypadek użycia:** Wyszukanie zaległych płatności

- **Wejście:** Filtr łączy status PENDING, deadline przed teraz i tenant A.
- **Przebieg:** Query Object składa wyrażenia oraz parametry, adapter tłumaczy je na bezpieczne SQL.
- **Oczekiwany wynik:** Reguła filtrowania jest jawna i nie wymaga sklejania fragmentów od klienta.
- **Błąd lub granica:** Dowolny predykat Javy nie musi dać się efektywnie przetłumaczyć na SQL; trzeba
  określić wspierany język zapytań.

**Kiedy zastosować:** Dla wielu legalnych kombinacji zapytań. Generyczny DSL nie powinien pozwalać klientowi
dowolnie wskazywać kosztownych lub prywatnych kolumn.

**Koszt i pułapka:** Ogólny DSL może powielić silnik bazy; indeksy i limit kosztu nadal trzeba projektować.

**Rozwinięcie i ślad wykonania:** Query Object przechowuje parametry i kryteria, a executor tłumaczy je na
bezpieczne zapytanie. Oddziel dane od fragmentów składni, zwłaszcza sortowanie i nazwy kolumn. Dopuszczalny
zestaw pól oraz limit strony są częścią kontraktu kosztu, nie wyłącznie ochroną przed SQL injection.

**Ćwiczenie sprawdzające:** Połącz dwa filtry i cursor, zachowując parametryzację oraz stabilny porządek.

## Association Ownership / Optimized Collection Update

**Intuicja:** Obiektowa relacja dwukierunkowa ma dwa widoki, lecz baza przechowuje określone powiązanie.
Association Ownership ustala, która strona steruje zapisem, a świadome aktualizowanie kolekcji pozwala zmienić
jedną pozycję bez przebudowania całego zbioru. Błąd może ujawnić się dopiero w generowanych operacjach
persystencji.

**Problem i mechanizm:** Zmiany powiązań są zapisywane przez jawnego właściciela z porównaniem stanu i
minimalnym potrzebnym zapisem.

**Role i współpraca:** Root utrzymuje spójność w pamięci, mapper rozpoznaje dodania i usunięcia, klucz
elementu pozwala na precyzyjny zapis. Orphan removal ma jawne znaczenie lifecycle.

**Przykład:** Dodanie jednej pozycji Order tworzy jeden INSERT, a nie przebudowę całej tabeli pozycji.

**Mechanizm w kodzie:** Szablon SQL: wymaga tabel, parametrów i transakcji właściwych dla używanego silnika.
Fragment ilustruje mechanizm; nie jest automatycznie wykonywany przez kontrolę dokumentacji.

```sql
INSERT INTO order_lines(id, order_id, sku, quantity)
VALUES (:line_id, :order_id, :sku, :quantity);
-- Dodanie jednej pozycji nie musi usuwać wszystkich pozostałych.
DELETE FROM order_lines WHERE id = :removed_line AND order_id = :order_id;
```

**Konkretny przypadek użycia:** Zmiana jednej pozycji dużego koszyka

- **Wejście:** Koszyk ma 500 pozycji, użytkownik usuwa produkt P.
- **Przebieg:** Strona właścicielska relacji rejestruje różnicę, zapis usuwa wskazane powiązanie.
- **Oczekiwany wynik:** Nie trzeba kasować i wstawiać ponownie wszystkich 500 relacji.
- **Błąd lub granica:** Niepoprawne equals i hashCode albo aktualizacja niewłaścicielskiej strony może zgubić
  zmianę w ORM.

**Kiedy zastosować:** Gdy kolekcje są większe i zmieniają się przyrostowo. Sprawdzaj SQL, a nie tylko to, że
końcowa lista wygląda poprawnie w jednym procesie.

**Koszt i pułapka:** Niepoprawny owner albo orphan removal może usuwać dane lub gubić aktualizacje; szczegóły
zależą od ORM.

**Rozwinięcie i ślad wykonania:** Dodanie jednej pozycji powinno prowadzić do zrozumiałego zestawu operacji,
nie przypadkowego usunięcia całej kolekcji. Sprawdź mapowanie owner, klucze elementów i orphan removal na
realnej bazie. Test samej listy w pamięci nie wykryje nadmiarowego DELETE oraz INSERT.

**Ćwiczenie sprawdzające:** Policz SQL dla dodania, usunięcia i przestawienia jednej pozycji.

## Coarse-grained Lock / Implicit Lock

**Intuicja:** Reguła może dotyczyć grupy rekordów, więc osobne wersje każdej pozycji nie zawsze wystarczą.
Coarse-grained Lock chroni szerszą granicę jednym prawem lub wersją. Implicit Lock przenosi obowiązek
egzekwowania na uzgodnioną infrastrukturę, zamiast wymagać ręcznej pamięci w każdej metodzie.

**Problem i mechanizm:** Jeden mechanizm blokady chroni grupę powiązanych zmian albo jest automatycznie
egzekwowany przez infrastrukturę.

**Role i współpraca:** Wersja root wyznacza granicę, każdy zapis składnika musi ją sprawdzić i zwiększyć.
Ominięcie root przez skrypt lub import unieważnia ochronę.

**Przykład:** Wersja root Order chroni również zmiany pozycji, jeśli każdy zapis poprawnie podnosi wersję
właściciela.

**Mechanizm w kodzie:** Szablon SQL: wymaga tabel, parametrów i transakcji właściwych dla używanego silnika.
Fragment ilustruje mechanizm; nie jest automatycznie wykonywany przez kontrolę dokumentacji.

```sql
BEGIN;
UPDATE orders SET version = version + 1
WHERE id = :order_id AND version = :expected;
-- Kontynuuj tylko po udanym przejęciu wersji; w przeciwnym razie rollback.
UPDATE order_lines SET quantity = :qty WHERE id = :line_id AND order_id = :order_id;
COMMIT;
```

**Konkretny przypadek użycia:** Blokada całego zamówienia

- **Wejście:** Dwa procesy zmieniają różne pozycje tego samego agregatu z limitem sumy.
- **Przebieg:** Coarse-grained lock chroni wspólny niezmiennik, zamiast niezależnie blokować każdą pozycję.
- **Oczekiwany wynik:** Drugi proces czeka i ocenia limit na aktualnym stanie.
- **Błąd lub granica:** Szeroka blokada ogranicza przepustowość; implicit lock nadal wymaga znajomości momentu
  pobrania i zwolnienia blokad.

**Kiedy zastosować:** Dla agregatów i reguł obejmujących kilka rekordów. Szerszy zakres zwiększa konflikty,
dlatego nie powinien rosnąć razem z każdym luźnym powiązaniem.

**Koszt i pułapka:** Sama wersja root nie pomaga, gdy osobny zapis pozycji ją omija; szersza blokada zwiększa
konflikty.

**Rozwinięcie i ślad wykonania:** Jedna wersja root może chronić wiele pozycji tylko wtedy, gdy każda ścieżka
ich zmiany sprawdza i zwiększa tę wersję. Bezpośredni UPDATE pozycji omijający root łamie kontrakt. To samo
dotyczy importów, zadań naprawy i skryptów administracyjnych, nie tylko API aplikacji.

**Ćwiczenie sprawdzające:** Pokaż, że zmiana pozycji przez boczną ścieżkę nie omija niezmiennika root.

## Client Session State / Server Session State / Database Session State

**Intuicja:** Sesja użytkownika trwa dłużej niż pojedynczy request i trzeba wybrać miejsce jej stanu. Client
Session State przenosi dane do klienta, Server Session State do pamięci serwera, Database Session State do
trwałego store. Różnią się kosztami transferu, failover, ochrony i sprzątania, nie tylko nazwą technologii.

**Problem i mechanizm:** Stan rozmowy przechowuje się po stronie klienta, w serwerze albo trwałej bazie według
potrzeb failover i rozmiaru.

**Role i współpraca:** SessionId wiąże kolejne żądania, wybrane źródło przechowuje stan, polityka określa czas
życia i współbieżną zmianę. Sesja rozmowy nie jest transakcją DB.

**Przykład:** Koszyk jest identyfikowany cookie, ale jego zawartość trwa w bazie; stan filtrów może zostać w
URL.

**Mechanizm w kodzie:** Pseudokod: nazwy oznaczają role i operacje z opisu. To schemat algorytmu lub
interakcji, bez deklaracji implementacji infrastruktury.

```text
Client: cookie lub token z kontrolowaną integralnością stanu
Server: sessionId -> lokalna pamięć instancji
Database: sessionId -> współdzielony trwały rekord

Restart jednej instancji ma inne skutki w każdym wariancie.
```

**Konkretny przypadek użycia:** Stan wieloetapowego formularza

- **Wejście:** Użytkownik ma trzy kroki formularza, wraca po 10 minutach i trafia do innej repliki.
- **Przebieg:** Client state przenosi dane z ochroną integralności, server state wymaga współdzielenia lub
  routingu, DB state przeżywa restart.
- **Oczekiwany wynik:** Wybrany wariant ma jawny czas życia i reguły wznowienia formularza.
- **Błąd lub granica:** Stan klienta może być nieaktualny, pamięć repliki może zniknąć, a trwały stan
  potrzebuje sprzątania i kontroli dostępu.

**Kiedy zastosować:** Według rozmiaru, prywatności i trwałości danych. Koszyk i filtr ekranu nie muszą
korzystać z tego samego wariantu.

**Koszt i pułapka:** Client state wymaga ochrony integralności, server state współdzielenia lub sticky
routing, database state cleanup i kontroli współbieżności.

**Rozwinięcie i ślad wykonania:** Stan w cookie potrzebuje ochrony integralności i limitu rozmiaru, stan
serwera planu failover, a stan bazy retencji oraz kontroli wersji. Sesja aplikacji nie jest tym samym co
transakcja DB. Można przenieść filtr ekranu do URL, nie przenosząc tam prywatnego koszyka.

**Ćwiczenie sprawdzające:** Zrestartuj serwer w połowie sesji i wskaż, które dane przetrwają.

## Remote Facade / Coarse-grained API

**Intuicja:** Lokalne drobne gettery są tanie, ale trzydzieści zdalnych getterów oznacza trzydzieści zależnych
oczekiwań sieciowych. Remote Facade oferuje większą operację dobraną do granicy zdalnej. Redukuje chattiness,
nie musi jednak odsłaniać całego obiektu domenowego ani przenosić wszystkich reguł do klienta.

**Problem i mechanizm:** Granica zdalna oferuje większe operacje niż lokalne drobne metody, aby ograniczyć
chattiness.

**Role i współpraca:** Facade przyjmuje zrozumiały zamiar lub query, podsystem składa wynik, DTO przenosi
dane. BFF może pełnić podobną rolę dla konkretnego rodzaju klienta.

**Przykład:** getOrderSummary zwraca potrzebny ekran zamiast 30 wywołań getField przez sieć.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
OrderSummary summary = remoteOrders.getSummary(orderId);
// Jedno wywołanie zamiast osobnych getStatus, getTotal i getDelivery.
```

**Konkretny przypadek użycia:** Jedno zdalne zatwierdzenie koszyka

- **Wejście:** Klient musiał dotąd wywołać sześć drobnych metod serwisu przez sieć.
- **Przebieg:** Remote Facade przyjmuje jedno polecenie checkout i koordynuje lokalny model.
- **Oczekiwany wynik:** Liczba przejść sieciowych spada, a kontrakt odpowiada operacji biznesowej.
- **Błąd lub granica:** Jedna zdalna metoda nie czyni zewnętrznej płatności i bazy wspólną transakcją.

**Kiedy zastosować:** Gdy koszt round-trip dominuje drobne wywołania. Duża odpowiedź nadal wymaga limitu,
kompatybilności i zrozumiałej polityki częściowego błędu.

**Koszt i pułapka:** Wielki payload i silne sprzężenie z jednym widokiem mogą zwiększyć koszt zmian; BFF bywa
właściwym miejscem kompozycji.

**Rozwinięcie i ślad wykonania:** Zdalne getSummary łączy dane potrzebne klientowi w uzgodnioną operację
zamiast serii drobnych getterów. Facade może redukować round-tripy, lecz nie powinna bez potrzeby zwracać
całego modelu domeny. Określ timeout, częściowy wynik i kompatybilność większego payloadu.

**Ćwiczenie sprawdzające:** Policz liczbę round-tripów oraz budżet latency przed i po agregacji.

## Separated Interface / Layer Supertype

**Intuicja:** Interfejs nie musi leżeć obok implementacji, której klient nie chce znać. Separated Interface
umieszcza kontrakt po stronie zależnej polityki i odwraca kierunek zależności. Layer Supertype to odrębny
pomysł współdzielenia zachowania warstwy przez klasę bazową; nie wynika automatycznie z wydzielenia
interfejsu.

**Problem i mechanizm:** Interfejs leży po stronie klienta polityki, a wspólne zachowanie warstwy może mieć
świadomie wąską klasę bazową.

**Role i współpraca:** Application posiada port Orders, infrastruktura implementację. Wspólna klasa bazowa
może udostępniać wąskie, stabilne zachowanie warstwy, nie dowolne pole dla wszystkich podklas.

**Przykład:** Port Orders jest w application, adapter SQL w infrastructure; BaseController zawiera tylko
uzgodnioną translację błędów.

**Mechanizm w kodzie:** Pseudokod: nazwy oznaczają role i operacje z opisu. To schemat algorytmu lub
interakcji, bez deklaracji implementacji infrastruktury.

```text
application/Orders.java        <- kontrakt potrzebny use case
infrastructure/SqlOrders.java  <- implements Orders

application nie importuje SqlOrders ani typów sterownika DB
```

**Konkretny przypadek użycia:** Port kalkulatora opłat

- **Wejście:** Domena potrzebuje kalkulacji opłaty, adapter integruje bibliotekę przewoźnika.
- **Przebieg:** Separated Interface trzyma kontrakt przy kliencie, implementacja zależy od tego kontraktu.
- **Oczekiwany wynik:** Domena nie importuje SDK; opcjonalny Layer Supertype porządkuje rzeczywiście wspólne
  zachowanie warstwy.
- **Błąd lub granica:** Wspólna klasa bazowa ze wszystkimi usługami tworzy ukryte sprzężenie i nie jest
  potrzebna do samej separacji interfejsu.

**Kiedy zastosować:** Gdy potrzebujesz izolacji technologii albo rzeczywiście wspólnego kontraktu warstwy. Nie
używaj dziedziczenia jako magazynu przypadkowych helperów.

**Koszt i pułapka:** Ogólna klasa bazowa z rosnącą liczbą pól sprzęga niezwiązane typy; preferuj kompozycję,
gdy zachowania są niezależne.

**Rozwinięcie i ślad wykonania:** Interfejs portu należy do klienta polityki, dlatego jego sygnatury nie
powinny wymuszać typów sterownika SQL. Layer Supertype może ograniczyć wspólny kod warstwy, ale każda nowa
metoda bazowa rozszerza sprzężenie podklas. Kompozycja jest lepsza, gdy zachowania są opcjonalne i niezależne.

**Ćwiczenie sprawdzające:** Przenieś interfejs z pakietu SQL do pakietu klienta i porównaj importy.

## Registry / Service Locator

**Intuicja:** Czasem implementację wybiera się po kluczu, np. format importu. Registry zbiera takie obiekty w
nazwanym zakresie. Service Locator udostępnia wyszukiwanie usług w runtime; użyty globalnie w dowolnej
metodzie może ukrywać wymagania obiektu i przesuwać błędy konfiguracji do późnego wywołania.

**Problem i mechanizm:** Nazwany rejestr udostępnia obiekty lub usługi na podstawie klucza w określonym
zakresie.

**Role i współpraca:** Rejestr wiąże klucz z providerem, klient korzysta z niego jako jawnej zależności.
Composition root powinien sprawdzić kompletność i konflikt rejestracji.

**Przykład:** PluginRegistry.readerFor("csv") wybiera zarejestrowany provider; globalne
Services.get(Database.class) wyszukuje zależność w runtime.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
ReaderProvider provider = registry.require(format);
Reader reader = provider.create();
return reader.parse(input);
// Registry samo jest jawną zależnością importera.
```

**Konkretny przypadek użycia:** Wybór lokalnego eksportera po nazwie

- **Wejście:** Narzędzie ma eksportery csv i json rejestrowane podczas startu.
- **Przebieg:** Registry mapuje nazwę na jawnie przygotowaną implementację w punkcie wyboru.
- **Oczekiwany wynik:** Nieznany format daje czytelny błąd, a nowe rozszerzenie dodaje wpis rejestracji.
- **Błąd lub granica:** Wyszukiwanie wszystkich zależności z globalnego Service Locator ukrywa wymagania
  konstrukcji obiektu.

**Kiedy zastosować:** Dla rozszerzeń o dynamicznym wyborze. Zwykłe wymagane zależności są zwykle czytelniejsze
w konstruktorze niż przez globalne lookup.

**Koszt i pułapka:** Rejestr rozszerzeń może być uzasadniony, ale ukrywanie wszystkich zależności utrudnia
testy i wykrycie błędów przy kompilacji.

**Rozwinięcie i ślad wykonania:** Registry wyboru pluginu jest jawną zależnością mechanizmu rozszerzeń.
Globalny Service Locator wewnątrz dowolnej metody ukrywa wymagania obiektu i może zawieść dopiero przy rzadkim
wywołaniu. Ustal zakres rejestru i moment walidacji kompletności zamiast polegać na przypadkowej kolejności
rejestracji.

**Ćwiczenie sprawdzające:** Porównaj jawny konstruktor z trzema zależnościami i ukryty globalny lookup.

## Special Case

**Intuicja:** Brak zwykłego klienta nie zawsze jest błędem: może istnieć anonimowy użytkownik o ograniczonych
prawach. Special Case reprezentuje taki znaczący wariant własnym obiektem, zamiast rozpraszać sprawdzanie
null. Musi zachować semantykę: timeout bazy nie może zostać zamieniony na „anonimowy klient” i ukryty.

**Problem i mechanizm:** Nietypowy wynik ma własny typ zgodny z kontraktem, aby klient nie powielał warunków
wyjątkowej sytuacji.

**Role i współpraca:** Kontrakt CustomerView dopuszcza nazwany wariant, AnonymousCustomer realizuje
ograniczone możliwości. Null Object jest szczególnym przypadkiem pustego działania, nie każdą specjalną
sytuacją.

**Przykład:** UnknownCustomer reprezentuje anonimowy widok z ograniczonymi możliwościami; Null Object jest
wariantem pustego zachowania.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
CustomerView view = authenticated
        ? customers.load(principal.customerId())
        : new AnonymousCustomer();
return view.availableActions();
```

**Konkretny przypadek użycia:** Brak danych profilu gościa

- **Wejście:** Zapytanie dotyczy użytkownika niezalogowanego, dla którego istnieje zdefiniowana polityka
  gościa.
- **Przebieg:** Special Case zwraca GuestProfile z ograniczonymi uprawnieniami i domyślnym wyglądem.
- **Oczekiwany wynik:** Klient może wykonać wspólną operację profilu bez rozproszonych warunków null.
- **Błąd lub granica:** Brak konta wymaganego do wypłaty powinien być błędem; obiekt gościa nie może udawać
  uprawnionego klienta.

**Kiedy zastosować:** Gdy nietypowy wynik ma legalne, powtarzalne zachowanie. Odmowa dostępu i awaria
infrastruktury powinny zachować własne znaczenie.

**Koszt i pułapka:** Nie należy udawać normalnego klienta dla błędu autoryzacji lub awarii bazy; przypadek
musi być semantycznie jawny.

**Rozwinięcie i ślad wykonania:** UnknownCustomer może udostępnić ograniczony widok anonimowy, ale nie
powinien wykonywać uprzywilejowanych operacji klienta. Brak rekordu, brak uprawnienia i awaria DB mają
odmienne znaczenia. Typ specjalnego przypadku powinien je wyjaśniać, a nie spłaszczać do wygodnego pustego
obiektu.

**Ćwiczenie sprawdzające:** Odróżnij anonimowego klienta od nieistniejącego ID i timeout repozytorium.
