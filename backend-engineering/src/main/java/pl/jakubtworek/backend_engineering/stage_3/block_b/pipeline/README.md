# pipeline

<!-- material-card:start -->
> [!IMPORTANT]
> **Karta materiału**
> - **Zakres:** `temat-zaawansowany`
> - **Uczy:** pipeline.
> - **Typowy błąd:** Uznanie pojedynczego wyniku dotyczącego „pipeline” za gwarancję bez sprawdzenia niezmiennika i failure modes.
> - **Najkrótsza weryfikacja:** `.\mvnw.cmd --batch-mode --no-transfer-progress test`
> - **Role klas:** brak klasy-kontrprzykładu; pozostałe typy są minimalnymi modelami pojęć opisanych niżej.
> - **Granica:** Model weryfikuje nazwany niezmiennik; nie implementuje produkcyjnego protokołu rozproszonego ani infrastruktury dostawcy.
<!-- material-card:end -->

## Pipeline telemetryczny checkoutu



`CheckoutTelemetryPipeline` łączy modele Stage 3B w jedną wykonywalną ścieżkę:

```text
traceparent → SERVER span → CLIENT span providera
                            ├─ exception + status ERROR
                            └─ log z trace_id i span_id
              └─ histogram latency z ograniczonymi atrybutami
```

To laboratorium odsłania mechanikę SDK. W aplikacji produkcyjnej span HTTP i span
klienta często utworzy Java Agent. Nie należy instalować tego przykładu obok
równoważnej auto-instrumentacji, ponieważ powstałyby zduplikowane spany.

## Przepływ kontekstu

1. `TraceHeaderPropagator` odczytuje W3C `traceparent` bez ustawiania globalnego stanu.
2. Span `SERVER` dostaje wyekstrahowany kontekst jako jawnego rodzica.
3. Span `CLIENT` providera dziedziczy aktywny kontekst serwera.
4. Bieżący kontekst oraz `x-request-id` są wstrzykiwane do nagłówków providera.
5. Przy błędzie wyjątek i status `ERROR` są zapisane na spanie zależności.
6. Log powstaje przed zamknięciem scope’u zależności, więc jego `span_id`
   wskazuje dokładnie wadliwy hop, a nie tylko span wejściowy.

Kolejność kroku 6 ma znaczenie. `catch` umieszczony poza zamkniętym scope’em
zobaczyłby już span rodzica i osłabił korelację log–trace.

## Histogram i kardynalność

Latency jest zapisywana jako OpenTelemetry `DoubleHistogram` w sekundach.
Histogram zachowuje rozkład i może zostać zagregowany do percentyli po eksporcie.
Pojedyncza średnia nie pokazuje ogona rozkładu i nie pozwala wiarygodnie obliczyć
p95/p99 między instancjami.

Atrybuty punktu metrycznego ograniczają się do template’u trasy, metody i klasy
statusu. `trace_id`, `request_id`, `orderId` oraz surowa ścieżka nie są wymiarami
metryki. `MetricCardinalityBudget` dodatkowo zatrzymuje pojawianie się kolejnych
wartości po przekroczeniu lokalnego budżetu. Jest to guardrail procesu, nie
zamiennik limitów i alertów w backendzie metryk.

## Test wykonawczy

`TelemetryPipelineTest` uruchamia lokalny OpenTelemetry SDK z
`InMemorySpanExporter` oraz `InMemoryMetricReader`. Test potwierdza:

- zachowanie trace ID otrzymanego w `traceparent`,
- poprawny wychodzący `traceparent` wskazujący span klienta,
- poprawne relacje parent–child,
- wyjątek i status `ERROR` na spanie providera,
- identyczne `trace_id` oraz `span_id` w logu i błędnym spanie,
- typ metryki `HISTOGRAM`, jeden pomiar i niskokardynalne atrybuty,
- zatrzymanie nowej wartości po wyczerpaniu budżetu kardynalności.

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress '-Dtest=TelemetryPipelineTest' test
```
