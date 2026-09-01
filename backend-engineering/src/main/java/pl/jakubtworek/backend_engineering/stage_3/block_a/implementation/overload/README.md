# overload

<!-- material-card:start -->
> [!IMPORTANT]
> **Karta materiału**
> - **Zakres:** `temat-zaawansowany`
> - **Uczy:** overload.
> - **Typowy błąd:** Uznanie pojedynczego wyniku dotyczącego „overload” za gwarancję bez sprawdzenia niezmiennika i failure modes.
> - **Najkrótsza weryfikacja:** `.\mvnw.cmd --batch-mode --no-transfer-progress "-Dtest=BoundedRequestExecutorTest,DeadlinePropagationTest,OverloadProtectedRemoteClientTest" test`
> - **Role klas:** `BoundedRequestExecutor` = `correct`.
> - **Granica:** Model weryfikuje nazwany niezmiennik; nie implementuje produkcyjnego protokołu rozproszonego ani infrastruktury dostawcy.
<!-- material-card:end -->

## Kontrola przeciążenia i budżet czasu



## Cel laboratorium

Mechanizmy resilience nie działają niezależnie. Retry bez deadline'u może
przeżyć request klienta, timeout bez anulowania pozostawia pracę w downstreamie,
a nieograniczona kolejka tylko przesuwa awarię w czasie. Ten pakiet pokazuje jedną
wykonywalną ścieżkę:

```text
bounded workers + bounded queue
  → load shedding
  → request deadline
  → circuit breaker
  → retry ograniczone wspólnym retry budget
  → krótszy deadline próby
  → bulkhead konkretnej zależności
  → timeout fizycznej próby
  → downstream z propagowanym deadline'em
```

Kolejność jest częścią kontraktu. Odrzucenie przy pełnej kolejce następuje przed
alokacją dalszej pracy. Circuit breaker obserwuje wynik logicznego requestu,
retry zużywa token dopiero przed kolejną fizyczną próbą, a bulkhead chroni
pojemność konkretnej zależności.

## Mapa kodu

| Element | Odpowiedzialność | Ważna granica |
| --- | --- | --- |
| `RequestDeadline` | absolutny deadline, propagacja nagłówka i wydzielanie budżetu dziecka | deadline potomka nigdy nie przekracza deadline'u rodzica |
| `RetryPolicy` | limit prób, maksymalny czas próby i rezerwa rodzica | timeout próby nie jest nowym timeoutem całej operacji |
| `RetryBudget` | współdzielona liczba dopuszczalnych ponowień | pierwsze próby nie zużywają tokenów |
| `SemaphoreBulkhead` | fail-fast limit concurrency dla jednej zależności | płatności i katalog nie dzielą tej samej puli |
| `BoundedRequestExecutor` | stała liczba workerów i ograniczona kolejka | po zapełnieniu następuje jawny load shedding |
| `OverloadProtectedRemoteClient` | kompozycja deadline, breakera, retry, bulkheadu i timeoutu | downstream otrzymuje deadline krótszy od wejściowego |

## Deadline zamiast lokalnych timeoutów

Jeżeli każda usługa rozpocznie własne `500 ms`, łańcuch kilku wywołań może
pracować znacznie dłużej niż request ma wartość dla klienta. `RequestDeadline`
przenosi absolutny moment końca w `X-Request-Deadline-Epoch-Millis`. Kolejny hop
oblicza pozostały czas i może go wyłącznie skrócić:

```text
deadline dziecka = min(
  teraz + maksymalny czas zależności,
  deadline rodzica - rezerwa na odpowiedź
)
```

Rezerwa nie gwarantuje sukcesu serializacji ani wysłania odpowiedzi. Chroni tylko
przed świadomym przekazaniem całego czasu zależności. Jeśli po odjęciu rezerwy nie
pozostaje dodatni budżet, wywołanie kończy się przed dotknięciem downstreamu.

Absolutny deadline oparty o wall clock wymaga kontrolowanego clock skew między
hostami. Czas trwania lokalnej próby powinien być mierzony zegarem monotonicznym
klienta HTTP. Produkcyjny protokół może używać standardowego nagłówka platformy
albo propagować pozostały budżet wraz z ochroną przed jego ponownym zwiększeniem.

## Retry budget i retry storm

`maxAttempts=3` ogranicza pojedynczy request, ale nie ogranicza całej instancji.
Przy awarii 20 logicznych requestów nadal może wygenerować 60 fizycznych prób.
W teście tego laboratorium wspólny budżet pięciu retry zmniejsza amplifikację:

```text
bez budżetu: 20 pierwszych prób + 40 retry = 60 wywołań
budżet 5:    20 pierwszych prób +  5 retry = 25 wywołań
```

To uproszczony budżet o stałej liczbie tokenów. W produkcji zwykle odnawia się go
w oknie czasu i wiąże z wolumenem udanych pierwszych prób, aby retry stanowiły
ograniczony procent ruchu. Potrzebne są osobne metryki logical requests, physical
attempts, consumed retry tokens, denied retry oraz skuteczności kolejnej próby.

Retry ma sens tylko dla błędu przejściowego i operacji idempotentnej albo
chronionej idempotency key. `BulkheadFullException`, przekroczony deadline,
błąd walidacji i otwarty circuit breaker nie powinny być automatycznie ponawiane.

## Bulkhead i ograniczanie concurrency

Dodanie wątków nie zwiększa pojemności bazy z pulą 20 połączeń ani zewnętrznego
API dopuszczającego 50 requestów równoległych. `SemaphoreBulkhead` ogranicza
liczbę operacji w locie i odrzuca nadmiar bez oczekiwania. Każda zależność ma
osobny bulkhead, więc zajęte sloty płatności nie blokują katalogu.

Virtual threads zmniejszają koszt blokowania w JVM, lecz nie zwiększają liczby
połączeń, limitu API ani pojemności downstreamu. Semaphore nadal jest potrzebny;
inaczej tańsze tworzenie wątków pozwoli jedynie szybciej przeciążyć zależność.

Bulkhead z kolejką wymaga osobnego limitu czasu oczekiwania. To laboratorium
wybiera fail-fast, aby request nie zużywał deadline'u w niewidocznej kolejce przed
wywołaniem zależności.

## Bounded queue, backpressure i load shedding

`BoundedRequestExecutor` ma stałą liczbę workerów i `ArrayBlockingQueue`.
Gdy oba zasoby są pełne, kolejne zadanie otrzymuje `LoadShedException`. Adapter
HTTP powinien przetłumaczyć decyzję na jawny `429` albo `503`, opcjonalnie z
`Retry-After`, zależnie od tego, czy limit dotyczy klienta czy pojemności usługi.

Ograniczona kolejka zapewnia trzy rzeczy:

- maksymalny koszt pamięci jest znany,
- czas oczekiwania nie rośnie bez końca,
- przeciążenie staje się widoczną decyzją zamiast późnego timeoutu.

Nie należy automatycznie używać `CallerRunsPolicy` w wątku obsługującym HTTP.
Przenosi ona koszt na upstream i może blokować wątki serwera w sposób trudny do
kontrolowania. Backpressure musi mieć jawny kontrakt na granicy protokołu.

## Timeout klienta a anulowanie pracy

`TimeoutExecutor` po timeoutcie wykonuje `Future.cancel(true)`. To żądanie
przerwania, nie gwarancja zatrzymania operacji:

- kod kooperacyjny kończy pracę po `InterruptedException` lub sprawdzeniu flagi,
- klient HTTP może anulować request na poziomie protokołu,
- kod ignorujący przerwanie może dalej zużywać CPU, połączenie lub lock,
- zewnętrzny serwer mógł już zacząć efekt biznesowy i nie musi go cofnąć.

`TimeoutCancellationSemanticsTest` wykonuje oba warianty. W drugim klient otrzymuje
timeout, ale test dowodzi, że downstream nadal pracuje. Dlatego timeout nie może
być przedstawiany jako rollback ani exactly-once. Dla efektów ubocznych potrzebne
są idempotencja, status operacji i rekoncyliacja.

## Metryki i decyzje operacyjne

| Mechanizm | Minimalne metryki | Sygnał problemu |
| --- | --- | --- |
| deadline | remaining budget na hop, deadline exceeded | request trafia do zależności bez czasu na odpowiedź |
| retry budget | consumed, denied, physical/logical ratio | retry zwiększają ruch podczas spadku success rate |
| bulkhead | active, capacity, rejected | jedna zależność osiągnęła limit concurrency |
| kolejka | active, queued, capacity, shed | napływ pracy przekracza tempo obsługi |
| timeout/cancel | timeout, cancel requested, cancel observed | praca trwa po odejściu klienta |
| circuit breaker | stan i fail-fast rejections | zależność nie wraca do stabilnego stanu |

Alert nie powinien opierać się wyłącznie na liczbie odrzuceń. Kontrolowany load
shedding może oznaczać, że zabezpieczenie działa prawidłowo. Potrzebny jest kontekst:
error rate użytkownika, długość zjawiska, wykorzystanie capacity i stan downstreamu.

## Wykonywalne scenariusze

| Test | Dowód |
| --- | --- |
| `DeadlinePropagationTest` | każdy hop zachowuje absolutny deadline, rezerwę i zakaz zwiększania budżetu |
| `OverloadProtectedRemoteClientTest` | retry storm jest ograniczony, bulkheady są izolowane, downstream dostaje krótszy deadline |
| `BoundedRequestExecutorTest` | worker + jedna pozycja kolejki przyjmują dwie prace, a trzecią odrzucają |
| `TimeoutCancellationSemanticsTest` | timeout zatrzymuje kod kooperacyjny, lecz nie zatrzymuje kodu ignorującego przerwanie |

Z katalogu `backend-engineering`:

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress "-Dtest=DeadlinePropagationTest,BoundedRequestExecutorTest,OverloadProtectedRemoteClientTest,TimeoutCancellationSemanticsTest" test
```

## Świadome uproszczenia

- Retry budget nie ma okna ani automatycznego odnawiania tokenów.
- Kolejka działa wewnątrz jednej JVM i nie zastępuje trwałego brokera.
- Deadline używa własnego nagłówka edukacyjnego, a nie standardu konkretnej platformy.
- Timeout opiera się na przerwaniu `Future`; prawdziwy klient powinien również
  ustawić connect/read/write timeout i wspierać anulowanie protokołu.
- Circuit breaker jest istniejącym modelem kolejnych porażek, nie oknem failure rate.
- Fairness między tenantami i osobne per-tenant bulkheady rozwija laboratorium
  [`../saas`](../saas/README.md); ten przykład nadal modeluje limit zależności.

Te uproszczenia są granicą przykładu, nie sugestią konfiguracji produkcyjnej.
