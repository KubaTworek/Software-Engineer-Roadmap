# metrics

<!-- material-card:start -->
> [!IMPORTANT]
> **Karta materiału**
> - **Zakres:** `temat-zaawansowany`
> - **Uczy:** metrics.
> - **Typowy błąd:** Uznanie pojedynczego wyniku dotyczącego „metrics” za gwarancję bez sprawdzenia niezmiennika i failure modes.
> - **Najkrótsza weryfikacja:** `.\mvnw.cmd --batch-mode --no-transfer-progress "-Dtest=SystemDesignMetricsTest" test`
> - **Role klas:** `SystemDesignMetricsDemo` = `simulation`.
> - **Granica:** Model weryfikuje nazwany niezmiennik; nie implementuje produkcyjnego protokołu rozproszonego ani infrastruktury dostawcy.
<!-- material-card:end -->

## Metryki skalowania i odporności



## Cel pakietu

Kod w tym katalogu zamienia twierdzenia architektoniczne w wartości, które można policzyć i później skonfrontować z pomiarem. Nie jest klientem Prometheusa ani systemem monitoringu. To neutralny względem frameworka model: wejścia pojemnościowe, wyniki analizy, reguły alarmowe i decyzje skalowania.

## Mapa zagadnień

| Pakiet | Pytanie, na które odpowiada | Najważniejsze klasy |
| --- | --- | --- |
| `capacity` | który zasób powinien nasycić się pierwszy? | `CapacityInput`, `CapacityCalculator`, `BottleneckAnalyzer` |
| `cache` | ile pracy bazy usuwa cache i czy Redis jest zdrowy? | `CacheImpactCalculator`, `RedisHealthCalculator` |
| `queue` | czy konsumenci nadążają i jak szybko starzeje się backlog? | `QueueMetrics`, `QueueHealthEvaluator` |
| `resilience` | czy retry naprawiają błędy, czy wzmacniają ruch? | `RetryCounters`, `RetryMetricsCalculator` |
| `scaling` | jaka strategia skalowania pasuje do obserwowanego limitu? | `ScalingInput`, `ScalingStrategyAdvisor` |
| pakiet główny | kiedy sygnał powinien stać się alarmem? | `MetricSnapshot`, `AlertRule`, `AlertEvaluator` |

## Model, pomiar i decyzja

Poprawny przepływ pracy ma trzy oddzielne kroki:

1. `CapacityInput` zapisuje założenia i jednostki: repliki, vCPU, sekundy CPU na request, udział ruchu oraz QPS.
2. `CapacityCalculator` wyznacza limit pierwszego rzędu, a `BottleneckAnalyzer` wskazuje hipotezę o najniższym limicie RPS.
3. Load test oraz metryki nasycenia potwierdzają albo obalają hipotezę. Dopiero wtedy powstaje decyzja o optymalizacji lub skalowaniu.

Model zakłada stabilny koszt jednostkowy i nie symuluje kolejek, blokad, GC, rozkładu latency ani nierównomiernego ruchu. `Double.POSITIVE_INFINITY` oznacza, że dana ścieżka nie korzysta z modelowanego zasobu, na przykład ma `writeRatio = 0`. Nie oznacza nieskończonej pojemności bazy.

## Jak interpretować sygnały

Pojedyncza metryka rzadko wystarcza. Wzrost p99 bez wzrostu saturacji może wskazywać problem downstreamu lub zmianę profilu ruchu. Wzrost retry success rate może wyglądać pozytywnie, ale jednocześnie zwiększać liczbę prób i obciążenie chorej zależności. Rosnąca długość kolejki jest mniej użyteczna bez wieku najstarszej wiadomości i porównania publish rate z consume rate.

Reguła alertowa powinna opisywać objaw wymagający działania, mieć stabilny próg lub okno oraz prowadzić do konkretnej reakcji. Modele w tym pakiecie pomagają utrzymać te pojęcia jawne, ale nie zastępują histogramów, backendu telemetrycznego, SLO ani runbooka.

## Uruchomienie

Katalog jest częścią głównego modułu `backend-engineering`, a nie osobnym projektem Maven:

```shell
cd backend-engineering
./mvnw --batch-mode --no-transfer-progress test
```

Na Windows użyj `mvnw.cmd`.
