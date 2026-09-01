# failure semantics

<!-- material-card:start -->
> [!IMPORTANT]
> **Karta materiału**
> - **Zakres:** `praktyka-produkcyjna`
> - **Uczy:** failure semantics.
> - **Typowy błąd:** Uznanie pojedynczego wyniku dotyczącego „failure semantics” za gwarancję bez sprawdzenia niezmiennika i failure modes.
> - **Najkrótsza weryfikacja:** `.\mvnw.cmd --batch-mode --no-transfer-progress "-Dtest=FencingTokenTest,IdempotentPaymentExecutorTest" test`
> - **Role klas:** `FencedRegister` = `correct`, `IdempotentPaymentExecutor` = `correct`; `InMemoryLeaseCoordinator` = `simulation`.
> - **Granica:** Laboratorium pokazuje kontrakt i failure modes; nie zastępuje pełnego testu end-to-end ani operacyjnej konfiguracji środowiska.
<!-- material-card:end -->

## Semantyka awarii w systemie rozproszonym



## Cel laboratorium

Awaria sieci nie mówi, co wydarzyło się po drugiej stronie. Klient może dostać timeout, mimo że serwer nie otrzymał żądania, wykonuje je nadal albo wykonał operację i utracił odpowiedź. Poprawny backend nie zamienia automatycznie braku odpowiedzi na informację o niepowodzeniu operacji biznesowej.

Laboratorium łączy trzy praktyczne scenariusze:

- `payment` — timeout po komendzie płatniczej, stabilny klucz idempotencji i stan `UNKNOWN`,
- `consumer` — retry, duplikaty, poison message, DLQ oraz commit offsetu,
- `lease` — wygaśnięcie dzierżawy i fencing token blokujący spóźnionego workera.

## Wynik biznesowy a wynik transportu

Po wysłaniu komendy istnieją trzy istotne wyniki biznesowe:

| Wynik | Co wiemy | Dalsza akcja |
|---|---|---|
| `AUTHORIZED` | dostawca jednoznacznie potwierdził efekt | zapisać identyfikator dostawcy i kontynuować proces |
| `REJECTED` | dostawca jednoznacznie odrzucił komendę | nie wykonywać retry tej samej decyzji bez zmiany danych |
| `UNKNOWN` | nie wiadomo, czy efekt wystąpił | odpytać status po kluczu albo ponowić z tym samym kluczem idempotencji |

HTTP `200`, `409`, `422`, `503` i timeout są sygnałami transportowymi. Ich semantyka biznesowa zależy od kontraktu dostawcy. Przykładowo `409` może oznaczać bezpieczny duplikat już wykonanej komendy, a timeout nie jest dowodem anulowania płatności.

`IdempotentPaymentExecutor` zawsze przekazuje ten sam `PaymentCommand`, więc wszystkie próby mają ten sam `idempotencyKey`. Jeżeli pierwsza próba wykonała płatność, ale utraciła odpowiedź, dostawca powinien zwrócić zapisany wynik zamiast tworzyć drugie obciążenie. Nowy klucz przy retry oznacza nową operację i niszczy tę gwarancję.

Klucz idempotencji musi być powiązany z fingerprintem komendy. Ten sam klucz użyty dla innego `orderId` albo kwoty powinien zostać odrzucony, a nie zwrócić historyczny wynik niepasującej operacji.

## Kiedy retry jest bezpieczny

Retry wymaga jednocześnie:

1. błędu sklasyfikowanego jako przejściowy,
2. idempotentnej operacji albo stabilnego klucza idempotencji,
3. limitu prób i całkowitego budżetu czasu,
4. backoffu z jitterem,
5. timeoutu krótszego od pozostałego deadline'u requestu,
6. obserwowalności liczby prób i wyniku końcowego.

Nie ponawiaj błędów walidacji, odmowy biznesowej, niezgodnego schematu ani błędu programistycznego. Automatyczne traktowanie każdego `RuntimeException` jako transient może zamienić pojedynczy błąd w retry storm.

Retry na kilku warstwach mnoży ruch. Trzy próby w gatewayu, trzy w serwisie i trzy w kliencie mogą dać do 27 wywołań jednej zależności. Jedna warstwa powinna być właścicielem retry, a pozostałe muszą respektować wspólny deadline.

Circuit breaker nie zastępuje timeoutu ani retry. Timeout ogranicza pojedynczą próbę, retry obsługuje krótką awarię, a circuit breaker ogranicza dalszy nacisk na zależność. Błędy klienta i odmowy biznesowe nie powinny pogarszać stanu circuit breakera.

## Gwarancje dostarczania

| Model | Co optymalizuje | Typowy koszt |
|---|---|---|
| at-most-once | brak duplikatów transportowych | możliwa utrata efektu po commicie offsetu przed obsługą |
| at-least-once | brak cichej utraty wiadomości | możliwe duplikaty; konsument musi być idempotentny |
| broker exactly-once | atomowość w granicach wspieranego ekosystemu brokera | nie obejmuje automatycznie zewnętrznej bazy lub operatora płatności |
| effectively-once | jeden efekt biznesowy mimo ponowień | unikalne klucze, transakcja lokalna i kontrolowane przejścia stanu |

Bezpieczny konsument zapisuje marker `eventId`, efekt biznesowy i ewentualny outbox w jednej lokalnej transakcji. Offset zatwierdza dopiero po tym commicie. Crash pomiędzy commitem bazy i offsetu spowoduje redelivery, ale unikalny marker zamieni je w bezpieczny duplikat.

## Poison message i DLQ

Błąd przejściowy może zniknąć: chwilowy timeout bazy, `503` albo zerwane połączenie. Poison message nie stanie się poprawny od czekania: ma zły schemat, brak wymaganej wartości, nieobsługiwaną wersję albo trwałe naruszenie reguły biznesowej.

Po wyczerpaniu retry wynik to `RETRIES_EXHAUSTED`, a nie `NON_RETRYABLE_FAILURE`. Pierwszy mówi „błąd uznaliśmy za przejściowy, ale nie odzyskał sprawności w budżecie”, drugi — „kolejna identyczna próba nie ma sensu”. Rozróżnienie jest potrzebne w DLQ, alarmach i runbooku.

Publikacja do DLQ musi zakończyć się przed commitem offsetu. W przeciwnym razie awaria DLQ spowoduje utratę komunikatu. To nadal dual-write; dla Kafka→Kafka można użyć transakcji Kafki, a przy innym zasobie potrzebny jest trwały zapis pośredni.

DLQ nie jest końcem obsługi. Rekord powinien zawierać oryginalny payload i nagłówki, topic, partycję, offset, `eventId`, `correlationId`, kod błędu, liczbę prób oraz czas. Replay powinien być kontrolowaną operacją z audytem, limitem szybkości i ponowną walidacją idempotencji.

## Lease nie jest blokadą

Dzierżawa wygasa według czasu koordynatora. Worker może jednak zatrzymać się przez długi GC pause, utracić sieć albo nie zauważyć wygaśnięcia. W tym czasie inny worker uzyska nową dzierżawę. Po wznowieniu stary worker nadal może próbować zapisać wynik.

`InMemoryLeaseCoordinator` wydaje rosnący `fencingToken`. `FencedRegister` zapamiętuje największy zaakceptowany token i odrzuca każdy starszy. Dzięki temu kolejność przyjęta przez zasób docelowy jest ważniejsza niż przekonanie workera, że nadal jest właścicielem lease.

```text
worker A: token 41 ── pauza ─────────────── próba zapisu (odrzucona)
worker B:             token 42 ── zapis (zaakceptowany)
```

Przykład koordynatora jest lokalnym modelem dydaktycznym. Produkcyjne tokeny muszą pochodzić z liniaryzowalnego, trwałego mechanizmu, a zasób docelowy musi atomowo porównywać i zapisywać token razem z chronioną zmianą. Sam Redis lock z TTL bez tokenu nie powstrzymuje spóźnionego właściciela.

## Pytania kontrolne

1. Czy timeout oznacza porażkę, czy nieznany wynik?
2. Czy każda próba używa tego samego klucza i fingerprintu?
3. Która warstwa jest właścicielem retry i jaki ma deadline?
4. Czy duplikat może wykonać zewnętrzny efekt drugi raz?
5. Czy marker deduplikacji i efekt biznesowy są w jednej transakcji?
6. Czy DLQ odróżnia poison message od wyczerpanego błędu przejściowego?
7. Czy commit offsetu następuje dopiero po trwałym wyniku lub trwałym DLQ?
8. Czy chroniony zasób weryfikuje fencing token, czy ufa wyłącznie TTL?
