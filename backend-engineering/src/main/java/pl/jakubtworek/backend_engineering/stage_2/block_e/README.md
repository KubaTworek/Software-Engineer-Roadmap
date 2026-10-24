# Batch i trwałe procesy — Spring Batch oraz Temporal

<!-- material-card:start -->
> [!IMPORTANT]
> **Karta materiału**
> - **Zakres:** `praktyka-produkcyjna`
> - **Uczy:** Batch i trwałe procesy — Spring Batch oraz Temporal.
> - **Typowy błąd:** Zapis checkpointu poza transakcją efektu lub uznanie retry Activity za exactly-once.
> - **Najkrótsza weryfikacja:** `.\mvnw.cmd --batch-mode --no-transfer-progress -f labs/durable-processes/pom.xml verify`
> - **Katalog uruchomienia:** `backend-engineering` (katalog głównego modułu).
> - **Role klas:** brak jawnej roli w nazwach klas; intencję przykładu określają opis i test, nie sam brak prefiksu.
> - **Granica:** Laboratorium pokazuje kontrakt i failure modes; nie zastępuje pełnego testu end-to-end ani operacyjnej konfiguracji środowiska.
<!-- material-card:end -->

## Problem i niezmiennik

Restart procesu nie może zgubić postępu importu ani ponowić obciążenia płatności.
Te dwa problemy mają inne granice: porcja i checkpoint mogą dzielić transakcję DB,
ale efekt zewnętrznej Activity wymaga własnej idempotencji.

Implementacja znajduje się w [osobnym laboratorium](../../../../../../../../labs/durable-processes/README.md).
Nie jest to istniejący blok `temporal_correctness`: tamten dotyczy czasu i stref.

## Przepływ i kontrprzykłady

Spring Batch czyta uporządkowane, niezmienne wejście i zapisuje porcję 100 rekordów.
Awaria przy rekordzie 251 pozostawia 200 zatwierdzonych rekordów. Nowy Job i reader
wznawiają pracę z checkpointu JobRepository. Naiwny offset zapisany przed efektem
zgubiłby rekordy, a brak checkpointu powodowałby konflikty przy restarcie.

Temporal czeka do dwóch dni na Signal, wykonuje Activity charge, a potem fulfill.
Activity charge może wykonać efekt i utracić odpowiedź. Retry ponawia wywołanie,
więc provider deduplikuje stabilny klucz. Błąd fulfill uruchamia kompensację
zarejestrowaną przed charge, także na wypadek niepewnego wyniku operacji.
Błąd kompensacji nie jest zamieniany w sukces.

Workflow używa `Workflow.await`, stubów Activities i `getVersion`; nie używa
HTTP/JDBC, `Instant.now` ani zwykłego sleep. Replay odtwarza decyzje z historii.
Zmiana kolejności komend bez migracji historii może złamać uruchomione instancje.
Signals zmieniają stan, Queries go odczytują; Updates dodają kontrakt walidowanej
komendy. Retencja payloadów, sekrety, heartbeat długich Activities i Continue-As-New
wymagają decyzji odpowiednich do rzeczywistego procesu. Ten proces ma ograniczoną
historię i nie potrzebuje sztucznego Continue-As-New.

## Uruchomienie i dowody

Z `backend-engineering`:

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress -f labs/durable-processes/pom.xml verify
.\mvnw.cmd --batch-mode --no-transfer-progress -f labs/durable-processes/pom.xml "-Dimport.rows=1000000" "-Dtest=RestartableImportTest" test
```

Testy: `RestartableImportTest`, `ApprovalWorkflowTest`. SDK uruchamia lokalny
serwer testowy i time skipping, a replay weryfikuje historię zakończonego Workflow.
H2 sprawdza transakcyjny restart Batch, nie PostgreSQL locking ani odporność na
awarię dysku. [Projekt 2](../../../../../../../../../projects/project-2/docs/durable-processes.md)
dodaje staging importu na PostgreSQL i opcjonalny worker Temporal powiązany z domeną.

## Wybór i granice

Scheduler wystarczy do krótkiej pracy z trwałym stanem; Batch pasuje do importów,
Temporal do koordynacji długiego procesu, broker do rozgłaszania faktów.
Kompensacja jest kolejną zawodną operacją biznesową, nie rollbackiem systemu.
Test SDK nie jest dowodem restartu klastra Temporal ani dokładnie jednego efektu
u prawdziwego operatora płatności. Laboratorium oddziela te gwarancje jawnie.
Uzupełnienie testów: ForkedBatchRestartTest wznawia FAILED w drugiej JVM
z JobRepository na dysku. CancellationCompensationTest sprawdza cancel po charge,
heartbeat, oczekiwanie na koniec Activity i refund w detached scope. Termination
nie uruchamia cleanupu; SIGKILL może pozostawić STARTED. Rozwinięcie lifecycle
feedu (kwarantanna, COMPLETE/FAILED/CANCELLED, EOF, publikacja, eksport) jest w
linkowanym projekcie 2, bez kopiowania domeny marketplace do laboratorium.
