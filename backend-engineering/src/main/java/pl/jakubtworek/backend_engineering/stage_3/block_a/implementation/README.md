# implementation

<!-- material-card:start -->
> [!IMPORTANT]
> **Karta materiału**
> - **Zakres:** `temat-zaawansowany`
> - **Uczy:** implementation.
> - **Typowy błąd:** Uznanie pojedynczego wyniku dotyczącego „implementation” za gwarancję bez sprawdzenia niezmiennika i failure modes.
> - **Najkrótsza weryfikacja:** `.\mvnw.cmd --batch-mode --no-transfer-progress "-Dtest=LoadTestRunnerTest,BoundedRequestExecutorTest,DeadlinePropagationTest" test`
> - **Role klas:** `BoundedRequestExecutor` = `correct`.
> - **Granica:** Model weryfikuje nazwany niezmiennik; nie implementuje produkcyjnego protokołu rozproszonego ani infrastruktury dostawcy.
<!-- material-card:end -->

## System Design: kompozycja mechanizmów



## Cel katalogu

`implementation` pokazuje, jak z małych mechanizmów z `concepts` zbudować spójną
ścieżkę aplikacyjną. Nie jest drugim zestawem implementacji cache, retry czy circuit
breakera. Dzięki jednej wersji każdego mechanizmu kod, README i testy opisują ten sam
kontrakt.

| Element | Odpowiedzialność |
| --- | --- |
| `ProductApiService` | łączy limiter, cache-aside, port `PaymentGateway` i odporny klient płatności |
| `remote/ResilientRemoteClient` | ustala kolejność timeout → retry → circuit breaker → fallback |
| [`overload`](overload/README.md) | łączy deadline, retry budget, bulkhead, bounded queue, load shedding i anulowanie |
| [`saas`](saas/README.md) | izoluje tenantów, ich pojemność, cache i dane oraz propaguje usunięcie |
| [`tests`](tests/README.md) | prowadzi od capacity przez load/stress/spike/soak do guardrails i oceny autoskalowania |
| `concepts/*` | dostarcza kanoniczne, izolowane mechanizmy i kontrakty |

`ProductApiService` celowo nie ma lokalnych odpowiedników `RateLimiter` ani
`CacheAsideService`. Importuje typy z `concepts`, a więc jest klientem mechanizmów,
które wcześniej można poznać i przetestować osobno.

Pakiet [`saas`](saas/README.md) rozwija używany przez API identyfikator tenanta do
pełnej granicy architektonicznej. Pokazuje, dlaczego quota, klucz cache, metryki,
audyt i usunięcie danych muszą zachowywać tenant context również poza synchronicznym
requestem.

## Kanoniczna kolejność resilience

`ResilientRemoteClient` składa wywołanie w następujący sposób:

```text
fallback(
  circuit-breaker(
    retry(
      timeout(dependency-call)
    )
  )
)
```

Czytając od środka:

1. `TimeoutExecutor` ogranicza czas każdej fizycznej próby do zależności.
2. `RetryExecutor` ponawia tylko błąd uznany przez `RetryClassifier` za przejściowy.
3. `CircuitBreaker` widzi wynik jednego logicznego requestu: sukces albo błąd po
   wyczerpaniu retry.
4. `Fallback` obsługuje dopiero końcową porażkę albo odrzucenie przez otwarty obwód.

Taki układ nie jest uniwersalnym prawem każdej biblioteki. Jest świadomą polityką tego
laboratorium: krótkie zakłócenie może zostać obsłużone retry, a breaker nie otwiera się
po jednej nieudanej próbie, po której kolejna zakończyła się sukcesem. W systemie o
dużym ruchu breaker oparty o okno i failure rate może celowo liczyć każdą próbę. Ważne,
aby jednostka pomiaru była jawna i zgodna z progami.

## Dlaczego retry na zewnątrz circuit breakera bywa groźne

Antywzorzec omawiany przez test wygląda tak:

```text
retry(circuit-breaker(timeout(dependency-call)))
```

Jeżeli pierwsza próba otworzy breaker, kolejne iteracje retry mogą już tylko otrzymać
`CircuitBreakerOpenException`. Zależność nie jest wywoływana, ale klient nadal zużywa
próby, czas, wątek i budżet latencji. Szeroki klasyfikator typu „retry dla każdego
wyjątku” dodatkowo maskuje przyczynę: końcowym błędem staje się otwarty obwód, chociaż
pierwszą przyczyną była awaria zależności.

`ResilientRemoteClientTest.retryOutsideCircuitBreakerWastesAttemptsOnAlreadyOpenCircuit`
pokazuje wykonywalnie trzy wejścia do warstwy retry i tylko jedno rzeczywiste wywołanie
zależności. Jeśli retry musi znaleźć się na zewnątrz, klasyfikator powinien co najmniej
wykluczać `CircuitBreakerOpenException`, a metryki muszą odróżniać logical requests,
physical attempts i fail-fast rejections.

## Budżet czasu i amplifikacja

Wykonywalne rozwinięcie tego tematu znajduje się w
[`overload/README.md`](overload/README.md). Pokazuje absolutny deadline propagowany
między usługami, rezerwę czasu rodzica, wspólny retry budget i różnicę między
timeoutem klienta a rzeczywistym zatrzymaniem downstreamu.

Timeout pojedynczej próby nie jest timeoutem całej operacji. Przy `A` próbach,
request timeout `T` oraz opóźnieniach backoff `B1 ... B(A-1)` górne przybliżenie wynosi:

```text
total <= A * T + sum(backoff) + koszt planowania i fallbacku
```

Dla trzech prób po 200 ms i backoffów 50 ms oraz 100 ms sam lokalny budżet może osiągnąć
około 750 ms. Musi zmieścić się w deadline requestu przychodzącego i zostawić czas na
odpowiedź zastępczą. Prosty `TimeoutConfig` przechowuje connection timeout i timeout
próby; łączny deadline powinien zostać dodany w adapterze HTTP albo przez dojrzałą
bibliotekę resilience.

Retry wzmacnia również ruch. `100` logicznych requestów z maksymalnie trzema próbami
może wygenerować do `300` wywołań zależności. Dlatego retry wymaga:

- idempotentnej operacji albo idempotency key,
- wąskiego klasyfikatora błędów przejściowych,
- backoffu z jitterem,
- limitu prób i całkowitego deadline'u,
- metryk liczby prób, retry success ratio oraz retry amplification.

## Fallback a poprawność biznesowa

Fallback nie oznacza „zwróć dowolny sukces”. Bezpieczny wynik zależy od operacji:

| Operacja | Rozsądny fallback | Niebezpieczny fallback |
| --- | --- | --- |
| rekomendacje produktu | pusta lista i znacznik degradacji | ukrycie błędu bez metryki |
| katalog | ostatnia jawnie oznaczona wartość stale | nieaktualna cena przedstawiona jako bieżąca |
| rezerwacja płatności | błąd kontrolowany lub status `PENDING` po trwałym zapisie | informacja „płatność przyjęta” bez potwierdzenia |
| autoryzacja | zwykle fail closed | przyznanie dostępu przy awarii zależności |

Dlatego `ProductApiService.reservePayment` nie przekazuje fallbacku zwracającego
fikcyjny sukces. Przeciążenie i niedostępność płatności powinny zostać przetłumaczone
przez adapter API na jawny kontrakt biznesowy.

## Granice przykładów

Kod pokazuje semantykę, a nie zastępuje Resilience4j, klienta HTTP ani rozproszonego
limitera. W szczególności:

- circuit breaker liczy kolejne błędy, nie failure rate w ruchomym oknie;
- stan breakera, cache i token bucket istnieje tylko w jednej JVM;
- `Thread.sleep` w retry blokuje wątek i służy czytelności przykładu synchronicznego;
- anulowanie `Future` jest kooperacyjne — zależność ignorująca przerwanie może nadal
  wykonywać pracę po timeoutcie klienta;
- connection timeout z `TimeoutConfig` musi skonfigurować rzeczywisty klient sieciowy;
- fallback musi emitować metrykę i nie może fałszować wyniku operacji krytycznej.

## Testy

Najbardziej istotne scenariusze kompozycji znajdują się w
`ResilientRemoteClientTest`:

- trzy fizyczne próby są wykonane przed zarejestrowaniem logicznej porażki przez breaker;
- fallback uruchamia się dokładnie raz i widzi już stan `OPEN`;
- następny request jest odrzucany fail-fast bez dotknięcia zależności;
- kontrprzykład `retry(circuit-breaker(...))` marnuje próby na otwartym obwodzie.

`ProductApiServiceTest` sprawdza natomiast cały przepływ aplikacyjny: limiter działa
przed zależnościami, cache scala powtarzane odczyty, a przejściowy błąd payment gateway
jest ponawiany bez otwarcia breakera, jeżeli logiczny request ostatecznie się powiedzie.

Z katalogu `backend-engineering`:

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress '-Dtest=ResilientRemoteClientTest,CacheAsideAndCircuitBreakerTest,TimeoutExecutorTest' test
```

Pipeline przeciążeniowy:

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress "-Dtest=DeadlinePropagationTest,BoundedRequestExecutorTest,OverloadProtectedRemoteClientTest,TimeoutCancellationSemanticsTest" test
```

Metodologia wydajności całej usługi:

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress "-Dtest=LoadTestRunnerTest,SystemPerformanceMethodologyTest,PerformanceExperimentEvaluatorTest,PerformanceExperimentRunnerTest" test
```

Pełna weryfikacja modułu:

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress verify
```
