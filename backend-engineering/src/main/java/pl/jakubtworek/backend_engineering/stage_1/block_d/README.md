# Stage 1D — dane, persystencja i wyszukiwanie

<!-- material-card:start -->
> [!IMPORTANT]
> **Karta materiału**
> - **Zakres:** `fundament`
> - **Uczy:** Stage 1D — dane, persystencja i wyszukiwanie.
> - **Typowy błąd:** Uznanie pojedynczego wyniku dotyczącego „Stage 1D — dane, persystencja i wyszukiwanie” za gwarancję bez sprawdzenia niezmiennika i failure modes.
> - **Najkrótsza weryfikacja:** `.\mvnw.cmd --batch-mode --no-transfer-progress "-Dtest=PostgreSqlConcurrencyContainerTest,RedisAtomicCounterContainerTest,ConditionalDocumentStoreTest" test`
> - **Role klas:** `NaiveSearchProjection` = `naive`; `AtomicFixedWindowRateLimiter` = `correct`; `QuorumConfiguration` = `production-boundary`.
> - **Granica:** Przykład dowodzi mechanizmu w opisanej granicy; bez testu infrastrukturalnego nie dowodzi zachowania wielu procesów ani konkretnej usługi.
<!-- material-card:end -->

## SQL vs NoSQL — porównanie i wybór technologii



## Mapa materiału

| Ścieżka | Zawartość | Jak pracować |
|---|---|---|
| `sql/workload` | schemat bazowego workloadu | uruchom jako pierwszy |
| `sql/EXECUTABLE_LAB.md` | Testcontainers: Flyway, plany, paginacja, izolacja, locking i constrainty | uruchom jako automatyczną specyfikację PostgreSQL |
| `sql/index` | indeksy pojedyncze i złożone | porównuj `EXPLAIN (ANALYZE, BUFFERS)` |
| `sql/execution_plan` | czytanie planów wykonania | porównuj estymacje z rzeczywistymi wierszami |
| `sql/transaction` | izolacja i anomalie | użyj dwóch niezależnych sesji |
| `sql/lock` | blokady i deadlocki | wykonuj kroki zgodnie z komentarzami sesji A/B |
| `sql/n_plus_one` | koszt wielu round-tripów | porównaj N+1 z joinem i batchingiem |
| `sql/pagination` | offset i keyset pagination | porównaj płytką i głęboką stronę |
| `sql/migration` | kompatybilne migracje expand–migrate–contract | przejdź przez wdrożenie i rollback krok po kroku |
| `sql/connection_pool` | globalny budżet i dobór puli | połącz obliczenia z metrykami oraz load testem |
| `sql/scale` | replikacja i partycjonowanie | traktuj jako analizę decyzji |
| `nosql/*` | access patterns, CAS, quorum, replication lag, TTL i partycjonowanie | uruchom testy modeli gwarancji |
| [`search_engine`](search_engine/README.md) | inverted index, wersjonowanie, tombstones, ranking i `search_after` | `VersionedSearchIndexTest` |

Pliki SQL są laboratoriami dla PostgreSQL, nie migracjami uruchamianymi przy
starcie aplikacji. Fragmenty dotyczące blokad i izolacji celowo wymagają kilku
sesji i nie powinny być wykonywane bezrefleksyjnie jako jeden skrypt. Katalog
`sql/migration` jest wykonywany przez Flyway wyłącznie w celowanej suite
Testcontainers; dzięki temu ćwiczenie pokazuje historię migracji bez podpinania
edukacyjnego schematu do aplikacji.

Laboratorium wyszukiwania traktuje indeks jako odtwarzalny read model i pokazuje
ochronę przed starym zdarzeniem, tombstone oraz stabilny kursor. Produkcyjny
adapter może używać OpenSearch albo Elasticsearch, ale model projekcji,
wersjonowania i naprawy danych pozostaje niezależny od produktu.

## Jak podejmować decyzję o modelu danych

Nie zaczynaj od pytania „SQL czy NoSQL?”. Najpierw zapisz:

1. niezmienniki biznesowe, których nie wolno naruszyć,
2. operacje zapisu i ich wymagania transakcyjne,
3. access patterny odczytu wraz z sortowaniem i paginacją,
4. oczekiwany wolumen, rozkład kluczy i dopuszczalne opóźnienie,
5. wymagania read-your-writes oraz tolerancję na stary odczyt,
6. sposób naprawy danych po częściowej awarii.

Dopiero potem dobieraj model. Zamówienie i płatność mogą pozostać w relacyjnym
źródle prawdy, podczas gdy ich projekcja wyszukiwawcza, cache i historia zdarzeń
trafią do innych magazynów. To nie oznacza, że wszystkie magazyny są równorzędnym
źródłem prawdy. Każda projekcja potrzebuje właściciela, wersji danych, sposobu
odbudowy oraz jawnej semantyki opóźnienia.

## Co pokazuje kod, a co pokazują skrypty

- Klasy dokumentów pokazują kształt danych, defensive copies i granice agregatów.
  Obok nich małe modele wykonawcze pokazują CAS, quorum, replication lag,
  bucketing i atomową decyzję rate limitera.
- Modele in-memory nie emulują MongoDB, Redis, Cassandry ani Neo4j. Pokazują
  semantykę, którą produkcyjnie musi zagwarantować operacja konkretnego silnika.
- Granica TTL jest domknięta: wartość jest nieważna dokładnie w chwili
  `expiresAt`, a nie dopiero chwilę później.
- Pliki SQL pokazują mechanizm PostgreSQL i wymagają obserwacji planu, blokad,
  czasu oraz liczby buforów. Sam poprawny rezultat zapytania nie dowodzi, że plan
  jest dobry.

Nie przenoś wyników jednego silnika bezpośrednio na inny. Poziomy izolacji,
blokady, indeksy, plany wykonania i gwarancje transakcji mają implementacyjne
różnice nawet wtedy, gdy używają podobnych nazw.

SQL i NoSQL nie powinny być traktowane jako konkurujące hasła marketingowe, tylko jako różne podejścia do modelowania danych i budowania systemów. SQL daje silny, relacyjny model danych, transakcje, integralność, joiny i dużą elastyczność zapytań. NoSQL daje możliwość dopasowania modelu danych do konkretnych access patternów, często łatwiejsze skalowanie horyzontalne, wysoką wydajność dla prostych odczytów i zapisów oraz modele lepiej pasujące do dokumentów, grafów, cache albo danych rozproszonych. Dobra decyzja polega na zrozumieniu kompromisów, a nie na wybraniu technologii, która jest popularniejsza.

SQL jest zwykle lepszym wyborem, gdy dane mają naturalne relacje i wymagają silnej spójności. Jeżeli system obsługuje płatności, rezerwacje, faktury, zamówienia, salda kont, rozliczenia albo uprawnienia, relacyjna baza danych daje mechanizmy, które pomagają utrzymać poprawność danych. Klucze obce, ograniczenia unikalności, transakcje, poziomy izolacji i możliwość wykonywania joinów są bardzo ważne tam, gdzie niepoprawny stan danych jest realnym problemem biznesowym. W takich systemach koszt błędu często jest większy niż koszt trochę trudniejszego skalowania.

SQL dobrze sprawdza się również wtedy, gdy zapytania są złożone albo zmieniają się w czasie. Jeżeli aplikacja potrzebuje raportów, filtrowania po wielu polach, sortowania, agregacji, joinów i ad hoc query, relacyjny model daje dużą elastyczność. Można dodać indeks, zmienić zapytanie, połączyć tabele i uzyskać nowy widok danych bez przebudowy całego modelu. To nie oznacza, że SQL jest automatycznie szybki. Nadal trzeba rozumieć indeksy, plany wykonania, koszt joinów i transakcje. Jednak SQL daje więcej swobody, gdy wymagania dotyczące zapytań nie są znane z góry.

NoSQL jest często lepszym wyborem, gdy access patterny są znane, bardzo konkretne i wymagają dużej skali. Jeśli system musi obsługiwać ogromną liczbę prostych zapisów, eventów, metryk, sesji, odczytów po kluczu albo danych dokumentowych, dobrze dobrana baza NoSQL może być prostsza i wydajniejsza niż próba dopasowania wszystkiego do relacyjnego modelu. Przykładem może być Redis dla sesji i cache, Cassandra dla eventów użytkownika lub metryk urządzeń, MongoDB dla dokumentów czy Neo4j dla głębokich relacji grafowych.

Najważniejsza różnica dotyczy modelowania. W SQL najczęściej dążysz do normalizacji i usuwania duplikacji. Dane mają jedno źródło prawdy, a relacje są wyrażone przez klucze. W NoSQL duplikacja często jest świadomym narzędziem. Dane mogą być zapisane w kilku miejscach, jeżeli dzięki temu odczyt jest prosty, szybki i zgodny z najważniejszym endpointem. Taki model poprawia performance odczytu, ale przenosi koszt na zapisy, synchronizację i obsługę niespójności. To jest podstawowy trade-off: SQL częściej płaci kosztem joinów przy odczycie, NoSQL częściej płaci kosztem duplikacji i utrzymania wielu projekcji danych.

Spójność to kolejna kluczowa różnica. SQL-owe bazy relacyjne zwykle oferują silne transakcje ACID i precyzyjne poziomy izolacji. Dzięki temu łatwiej budować operacje, które muszą być poprawne nawet przy równoczesnym dostępie wielu użytkowników. NoSQL może również oferować transakcje, ale ich zakres, koszt i ograniczenia zależą od konkretnej bazy. W wielu architekturach NoSQL częściej akceptuje się eventual consistency. To może być dobre dla feedów, wyszukiwarki, liczników, read modeli czy rekomendacji, ale nie zawsze nadaje się do operacji krytycznych finansowo lub bezpieczeństwa.

Wydajność również należy rozumieć inaczej. W SQL problemem często jest źle dobrany indeks, nieoptymalny join, sortowanie, N+1, zbyt duża liczba przetwarzanych rekordów albo niewłaściwy poziom izolacji. Analiza zaczyna się od `EXPLAIN ANALYZE`, statystyk, indeksów i planu wykonania. W NoSQL problemem częściej jest zły partition key, hot partition, kosztowny scan, zbyt duży dokument, źle dobrany access pattern, replication lag albo próba wykonywania zapytań, do których model danych nie został zaprojektowany. W obu przypadkach nie wystarczy powiedzieć „baza jest wolna”. Trzeba rozumieć, jaki mechanizm powoduje koszt.

Skalowanie SQL i NoSQL też ma różne konsekwencje. Relacyjne bazy danych bardzo dobrze skalują się pionowo i mogą skalować się poziomo, ale sharding relacyjnego modelu bywa trudny, szczególnie gdy dużo zapytań przechodzi przez relacje między tabelami. NoSQL częściej jest projektowany od początku z myślą o partycjonowaniu i rozproszeniu danych, ale wymaga dobrania klucza partycji i zaakceptowania ograniczeń zapytań. To nie oznacza, że NoSQL zawsze skaluje się lepiej. Źle dobrany partition key może zniszczyć skalowanie równie skutecznie jak brak indeksu w SQL.

Dobrym praktycznym podejściem jest zaczynanie od wymagań, a nie od technologii. Trzeba zapytać, czy dane wymagają silnej spójności, czy zapytania są znane z góry, czy potrzebne są joiny, czy akceptowana jest denormalizacja, jaki jest wolumen odczytów i zapisów, czy opóźnione odczyty są dopuszczalne, jak będzie wyglądać skalowanie oraz kto będzie utrzymywał system. Jeżeli odpowiedzi wskazują na silne relacje, transakcje i zmienne zapytania, SQL będzie zwykle bezpieczniejszym wyborem. Jeżeli odpowiedzi wskazują na proste access patterny, ogromną skalę, dokumenty, grafy, cache albo time-series, NoSQL może być lepszym narzędziem.

W wielu realnych systemach najlepszą decyzją nie jest wybór wyłącznie SQL albo wyłącznie NoSQL, ale użycie obu podejść tam, gdzie mają sens. Relacyjna baza może być głównym źródłem prawdy dla zamówień i płatności, Redis może obsługiwać cache i sesje, Elasticsearch może wspierać wyszukiwanie, Cassandra może przechowywać eventy, a Neo4j może obsługiwać rekomendacje grafowe. Taka architektura jest jednak bardziej złożona. Każda dodatkowa baza oznacza więcej operacyjnego kosztu, więcej synchronizacji, więcej monitoringu i więcej możliwych niespójności. Polyglot persistence ma sens dopiero wtedy, gdy zysk jest większy niż złożoność.

Najprostsza reguła jest taka: SQL wybierasz wtedy, gdy potrzebujesz elastyczności zapytań, relacji, integralności i transakcji. NoSQL wybierasz wtedy, gdy masz dobrze znane access patterny i konkretna baza daje wyraźną przewagę dla danego modelu danych. Nie wybierasz NoSQL, żeby uniknąć nauki SQL. Nie wybierasz SQL, ignorując wymagania skali. Wybierasz narzędzie, którego ograniczenia rozumiesz i którego kompromisy pasują do problemu.
