# Weryfikacja poprawek — 25 października 2026

Kontynuacja [audytu z 24 października](audit-2026-10-24/README.md).
Zmiany dotyczą backend-engineering i projektów 1–3; repozytorium nie jest jedną aplikacją.
Poniższe gwarancje odnoszą się do opisanych kontraktów i testów, nie do każdej
możliwej historii ani gotowości produkcyjnej całego systemu.

## Poprawki i dowody

| ID | Zmiana | Dowód i granica |
| --- | --- | --- |
| A1 | Projekt 3: COMMIT intencji PENDING przed charge, osobna transakcja wykonania, worker obejmujący PENDING, jedno zamówienie na rezerwację | `OrderCommitRecoveryInfrastructureTest`: rzeczywisty PostgreSQL odrzuca INSERT intencji (zero charge), potem UPDATE końcowy (intencja pozostaje, retry odzyskuje PAID, jeden orderId w ledgerze i jeden outbox). Sieć/providera reprezentują adaptery testowe; pełny HTTP sprawdza osobny smoke. |
| A2 | Backend 1C: REQUIRES_NEW, wyjątki odmowy nie wycofują revocation, wspólny mutex rodziny | `RefreshFamilyTransactionTest` uruchamia rzeczywiste proxy Spring/JPA na H2; po wyjątku cała rodzina pozostaje odwołana. Nie jest to test dialektu PostgreSQL. |
| A3 | Projekt 2: UPDATE status WHERE status=UNKNOWN; tylko zwycięzca zapisuje fakt w tej samej transakcji | `PaymentReconciliationConcurrencyTest`: dwie transakcje PostgreSQL docierają do lookup przed CAS; jeden PaymentReserved. Nie obiecuje pojedynczego wywołania lookup. |
| A4 | Temporal: heartbeat, WAIT_CANCELLATION_COMPLETED, refund w detached cancellation scope | `CancellationCompensationTest`: cancel po charge kończy się Canceled dopiero po refundzie. `ApprovalWorkflowTest` wymaga COMPENSATION_FAILED po błędzie refundu. Termination i niekooperatywny provider wymagają osobnego recovery. |
| A5 | Projekt 1: PESSIMISTIC_WRITE obejmuje odczyt aktywności, revoke i zapis następcy | `PostgreSqlReservationLifecycleIT`: dwie rotacje przez proxy, jeden sukces, jedna odmowa, jeden aktywny następca. Projekt 1 nie implementuje polityki rodzin z backend 1C. |
| A6 | Projekt 1: callback po COMMIT izoluje wyjątki każdej invalidacji | `CacheInvalidationTest`, pełne testy rezerwacji i smoke. TTL ogranicza stary cache; nie dodano trwałego retry invalidacji. |
| A7 | Projekt 2: ograniczona pula bez kolejki, deadline, fallback, okno/epoka dowodu | `ShadowCatalogReaderTest` obejmuje zawieszony adapter ignorujący interrupt, nasycenie i reset po rollback. Nie ma I/O pod monitorem; telemetria jest best effort. |
| A8 | Backend GraphQL: Instrumentation przed resolverami, rozwinięty AST, zmienne, field authorization i DataLoader per request | Pięć `GraphQlHttpRuntimeTest`: aliasy/fragmenty/lista ze zmiennej są odrzucane bez odczytu, cache nie przechodzi między requestami, brak dostępu do non-null pola propaguje null. Projekt 2 także liczy `first` ze zmiennych i ich wartości domyślnych. |
| A9 | Projekt 2: plik → staging/kwarantanna/checkpoint → EOF/liczniki → atomowa publikacja → eksport; FAILED/resume i terminalne CANCELLED/COMPLETE | `SupplierCatalogImportIntegrationTest` używa PostgreSQL. `ForkedBatchRestartTest` uruchamia dwie osobne JVM z plikowym JobRepository: po FAILED zostaje 200 rekordów, restart daje 1000 bez duplikatów. Nie jest to test SIGKILL ani awarii dysku. |
| A10 | Projekt 2 secure: wymagany issuer i audience, brak publicznego domyślnego sekretu | `MarketplaceSecurityIntegrationTest`: poprawnie podpisane tokeny z obcym/brakującym iss lub aud dają 401 przez HTTP. Profil lokalny pozostaje jawnie otwarty. |
| A11 | Projekt 3: manifest treści orders/outbox, odtworzona usługa HTTP, terminalny retry i integralność payment/reservation/inbox; ruch w czasie awarii i recovery | Lokalne skrypty smoke/restore/operational. Restore zatrzymuje writer na czas snapshotu jednej bazy i nie emituje efektów z przywróconego outboxa. Nie dowodzi wielobazowego PITR ani cloud RPO/RTO. |
| A12 | Poprawiono komentarze o cache, ledgerze płatności, liczbę suite infrastrukturalnych i mapę buildów | Dokumentacja odróżnia model, adapter, wykonany test i wymaganie produkcyjne. Historyczne proby pozostają poza standardowym buildem; runner kieruje do regresji. |

Drill wykrył dodatkowo zbyt długie oczekiwanie katalogu na Redis. Konfiguracja
ma teraz command/connect timeout 200 ms, aby fallback do PostgreSQL mieścił
się w czasie odpowiedzi. Order Service ogranicza oczekiwanie na połączenie DB
do 2 s. Nie jest to deadline całego requestu ani gwarancja czasu wszystkich zapytań SQL.

Przegląd granicy bezpieczeństwa wykrył też globalny LoggingAspect drukujący
wartość wyniku serwisu, w tym surowy refresh token. Usunięto wartości wyników
i message wyjątków z tego aspektu. Test przez rzeczywiste proxy i przechwycony
output wymaga, aby wydany token nie trafił do logu. Nie jest to pełny audyt
wszystkich sinków telemetrii w repozytorium.

## Weryfikacja

Komendy uruchamia się z katalogu danego modułu. Buildy używały hostowego JDK 23
z target/release 21; obrazy aplikacji budowano na Java 21.

| Katalog | Komenda / scenariusz | Wynik |
| --- | --- | --- |
| backend-engineering | `mvnw.cmd --batch-mode --no-transfer-progress verify` | sukces: 474 testy, 1 pominięty |
| backend-engineering | `mvnw.cmd --batch-mode --no-transfer-progress -Pinfrastructure-tests verify` | sukces: rzeczywiste PostgreSQL, Redis, Kafka i OpenSearch |
| backend-engineering | `mvnw.cmd --batch-mode --no-transfer-progress -f labs/durable-processes/pom.xml verify` | sukces: 8 testów, w tym dwie JVM i cancel/refund |
| projects/project-1 | `mvnw.cmd --batch-mode --no-transfer-progress -Pinfrastructure-tests verify` | sukces: 107 testów szybkich (1 pominięty) i 8 infrastrukturalnych |
| projects/project-2 | `gradlew.bat test integrationTest check assemble` | sukces: unit, integracje PostgreSQL/Kafka, HTTP i build |
| projects/project-3 | `mvnw.cmd --batch-mode --no-transfer-progress verify` i osobno `-Pinfrastructure-tests verify` | sukces wszystkich 8 modułów |
| projects/project-3 | `mvnw.cmd -pl catalog-service,order-service -am -Pinfrastructure-tests verify` | sukces po końcowej zmianie timeoutów i migracji |
| projects/project-3 | prywatny Compose `roadmap-remediation-20261025`, smoke, restore, operational drill | sukces smoke i restore; recovery Redis 19,40 s, RabbitMQ 31,29 s, order-db 21,34 s |
| katalog główny | `pwsh -File scripts/check-documentation.ps1`, `git diff --check` | sukces: 313 dokumentów, 1822 lokalnych linków, karty materiałów; brak błędów diff |

Czasy drilla obejmują zatrzymanie/start kontenera, ruch, recovery i sprawdzenie
zbieżności. Nie są percentylami latency ani pomiarem RTO dla utraty danych.
Podczas awarii order-db requesty kończyły się błędem/timeoutem; retry tego samego
klucza po recovery potwierdził jeden poprawny przepływ i dostarczenie zdarzenia.

Testy wymagające natywnego protoc, agenta JVM i Docker Engine wykonywano poza
ograniczeniami sandboxa. Pierwszy sandboxowy protoc/attach nie potwierdził buildu;
wynik sukcesu pochodzi z ponownego pełnego wykonania. Nie uruchamiano skanu NVD,
mutation testing, wdrożenia Terraform, cloud HA ani długiego soak testu.

## Ocena pokrycia merytorycznego

Nie potrzeba dodawać kolejnego etapu tylko po to, aby pomieścić więcej nazw.
Istotna jest siła dowodu w istniejącym etapie. W obecnym zakresie naprawiono
wskazane okna awarii i poszerzono testy, lecz nie można uczciwie zadeklarować
„pełnej poprawności backend engineering”. W szczególności:

- **Finanse i tożsamość:** provider w projekcie 3 nadal jest symulatorem, cena
  jest uproszczona, a userId w lokalnym labie nie zastępuje wdrożonej tożsamości
  klienta. Settlement, webhook ordering, chargeback, waluty/zaokrąglenia i rotacja
  kluczy IdP wymagają osobnego kontraktu i testów. Nie wywodź tych gwarancji z retry.
- **Trwałe procesy:** restart po FAILED jest inny niż SIGKILL z metadata STARTED.
  Naprawa wymaga potwierdzenia śmierci workera/fencingu. H2 na dysku i test server
  Temporal nie dowodzą trwałości klastra po utracie dysku lub regionu.
- **Modernizacja i import:** opublikowany feed nie nadpisuje automatycznie Product.
  Ownership SKU, waluty, kolejność wersji dostawcy, seryjna publikacja i migracja
  zapisu to jawne zadania przed podłączeniem do biznesowego katalogu.
- **API i obciążenie:** DataLoader deduplikuje ID w request; obecny port pamięciowy
  nie dowodzi SQL batching. Limity GraphQL nie zastępują request/body limit,
  deadline storage ani ochrony przed szerokim ruchem wielu małych zapytań.
- **Operacje:** quiesced restore jednej bazy, krótka awaria kontenera i model lag
  nie dowodzą spójnego PITR wszystkich usług, failoveru, RPO/RTO ani wydajności
  pod długotrwałą presją. Potrzebny jest izolowany drill wielu baz i mierzone SLO.
- **Prowadzenie pracy:** RFC, review, postmortem i komunikacja mają konkretne
  artefakty w roadmapie, ale symulacja nie jest doświadczeniem pracy z ludźmi.

Te ograniczenia są częścią materiału: określają, co przenieść do kolejnego
eksperymentu i czego nie wolno obiecywać na podstawie lokalnego zielonego buildu.

Końcowy restore na aktualnym obrazie Order Service: PASS, 56,95 s,
porównanie treści i retry z oryginalnym Idempotency-Key przez odtworzone HTTP.
Prywatny stack testowy, jego wolumeny i kontener restore zostały usunięte.
Zmiany pozostają lokalne na master; nie wykonano commitu, push ani merge.

Dalsze rozszerzenia i aktualne dowody tych granic opisuje
[domknięcie roadmapy](completion-2026-10-25.md). Ten raport zachowuje stan
bezpośrednio po pierwotnym audycie i nie nadpisuje późniejszych wyników.
