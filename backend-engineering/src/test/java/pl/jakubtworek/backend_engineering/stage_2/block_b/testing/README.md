# Deterministyczne testowanie współbieżności

<!-- material-card:start -->
> [!IMPORTANT]
> **Karta materiału**
> - **Zakres:** `praktyka-produkcyjna`
> - **Uczy:** Deterministyczne testowanie współbieżności.
> - **Typowy błąd:** Uznanie pojedynczego wyniku dotyczącego „Deterministyczne testowanie współbieżności” za gwarancję bez sprawdzenia niezmiennika i failure modes.
> - **Najkrótsza weryfikacja:** `.\mvnw.cmd --batch-mode --no-transfer-progress "-Dtest=KafkaPostgresSemanticsTest,CompensationTest,ConsumerCrashTest" test`
> - **Role klas:** `FakeKafkaBroker` = `simulation`, `InMemoryEventSink` = `simulation`, `InMemoryOutbox` = `simulation` (+2); `FailingPaymentConsumer` = `production-boundary`, `OutboxPublisher` = `production-boundary`, `TestablePaymentConsumer` = `production-boundary`.
> - **Granica:** Laboratorium pokazuje kontrakt i failure modes; nie zastępuje pełnego testu end-to-end ani operacyjnej konfiguracji środowiska.
<!-- material-card:end -->

## Testowanie architektury event-driven



Testy tego pakietu mają dwie uzupełniające się role. Szybkie testy z fake'ami
sprawdzają decyzje aplikacyjne i pozwalają precyzyjnie wstrzykiwać błędy.
`KafkaPostgresSemanticsTest` uruchamia natomiast prawdziwe Kafka i PostgreSQL,
aby potwierdzić zachowanie partycji, offsetów, unikalnych ograniczeń i transakcji.

Fake nie jest zamiennikiem brokera. Nie potwierdzi rebalansu, położenia grupy,
redelivery ani porządku w partycji. Kontener nie jest z kolei dobrym narzędziem
do testowania każdej gałęzi klasyfikacji błędów. W kompendium potrzebne są oba
poziomy.

## Piramida testów dla consumera

| Poziom | Co sprawdza | Przykłady w pakiecie |
|---|---|---|
| jednostkowy | klasyfikację błędu, deduplikację, retry i kompensację | `IdempotencyTest`, `CompensationTest` |
| harness pamięciowy | kolejność kroków oraz kontrolowany crash | `ConsumerCrashTest`, `OutboxBrokerFailureTest` |
| Kafka + PostgreSQL | rzeczywiste offsety, partycje i transakcje SQL | `KafkaPostgresSemanticsTest` |

## Wykonywalna specyfikacja Kafka + PostgreSQL

Suite `testing/integration/KafkaPostgresSemanticsTest.java` zawiera sześć
celowanych eksperymentów:

| Scenariusz | Obserwowana gwarancja | Najważniejsza granica |
|---|---|---|
| pięć zdarzeń z tym samym `orderId` | jeden klucz trafia do jednej partycji, a offsety zachowują kolejność | Kafka nie daje globalnego porządku między partycjami |
| zamknięcie consumera bez commitu | nowy consumer tej samej grupy otrzymuje ten sam offset | redelivery jest normalną częścią `at-least-once` |
| rollback PostgreSQL przed commitem offsetu | rekord wraca, a niedokończony efekt SQL znika | offset zatwierdzamy dopiero po commicie bazy |
| dwa rekordy z tym samym `eventId` | `PRIMARY KEY` markera pozwala wykonać efekt biznesowy raz | marker i efekt muszą być w tej samej transakcji |
| source → retry → DLQ | payload i klucz są zachowane, a nagłówki opisują źródło, próbę i błąd | publikacja do DLQ i commit offsetu nadal tworzą dual-write |
| crash po ack z Kafki, przed `sent_at` | outbox publikuje rekord ponownie | outbox gwarantuje brak utraty, nie brak duplikatów |

Test outboxa celowo otrzymuje dwa rekordy o tym samym `eventId`. Jest to poprawny
wynik eksperymentu: baza nie może atomowo objąć lokalnego `UPDATE sent_at` oraz
acknowledgement z Kafki. Odbiorca nadal potrzebuje idempotencji. Idempotentny
producer Kafka zapobiega części duplikatów powstałych podczas retry protokołu
producenta, ale nie scala dwóch świadomych wywołań `send()` po restarcie workera.

## Granica transakcji consumera

Bezpieczna kolejność dla efektu w lokalnym PostgreSQL to:

1. rozpoczęcie transakcji SQL,
2. próba zapisu `eventId` do `processed_events`,
3. wykonanie efektu biznesowego tylko dla nowego markera,
4. commit transakcji SQL,
5. commit następnego offsetu w Kafce (`record.offset() + 1`).

Crash przed krokiem 4 wycofuje marker i efekt. Crash między krokami 4 i 5
powoduje redelivery, ale unikalny marker zamienia duplikat w bezpieczne pominięcie.
Commit offsetu przed krokiem 4 jest niebezpieczny, bo po awarii Kafka nie musi już
ponownie dostarczyć rekordu, mimo że efekt biznesowy nie został utrwalony.

## Retry topic i DLQ

Przykład przenosi wraz z rekordem następujące nagłówki:

- `x-original-topic`,
- `x-original-partition`,
- `x-original-offset`,
- `x-retry-count`,
- `x-error-class`,
- `x-error-message`.

W systemie produkcyjnym warto dodać również `eventId`, `correlationId`, czas
pierwszej porażki i identyfikator wersji schematu. Nagłówki diagnostyczne nie
zastępują monitorowania retry/DLQ ani procedury kontrolowanego replayu.

## Uruchomienie

Wymagany jest działający Docker. Z katalogu `backend-engineering`:

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress -Pinfrastructure-tests '-Dtest=KafkaPostgresSemanticsTest' test
```

Suite używa obrazów `apache/kafka-native:3.8.0` i `postgres:16-alpine`.
Tag `infrastructure` wyłącza ją ze zwykłego `verify`. Profil infrastrukturalny
uruchamia test bez trybu cichego pomijania, więc niedostępny Docker powoduje
błąd, a nie pozornie poprawny build.

Szybki zestaw bez infrastruktury:

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress '-Dtest=DefaultIdempotentEventProcessorTest,KafkaEventConsumerTest,RetryConfigurationTest,KafkaConfigurationTest,CompensationTest,ConsumerCrashTest,IdempotencyTest,OutboxBrokerFailureTest' test
```
