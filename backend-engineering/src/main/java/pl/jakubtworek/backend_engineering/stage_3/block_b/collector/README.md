# collector

<!-- material-card:start -->
> [!IMPORTANT]
> **Karta materiału**
> - **Zakres:** `temat-zaawansowany`
> - **Uczy:** collector.
> - **Typowy błąd:** Uznanie pojedynczego wyniku dotyczącego „collector” za gwarancję bez sprawdzenia niezmiennika i failure modes.
> - **Najkrótsza weryfikacja:** `.\mvnw.cmd --batch-mode --no-transfer-progress test`
> - **Role klas:** brak klasy-kontrprzykładu; pozostałe typy są minimalnymi modelami pojęć opisanych niżej.
> - **Granica:** Model weryfikuje nazwany niezmiennik; nie implementuje produkcyjnego protokołu rozproszonego ani infrastruktury dostawcy.
<!-- material-card:end -->

## OpenTelemetry Collector i tail sampling



Plik `otel-collector-tail-sampling.yaml` jest kanonicznym przykładem konfiguracji
Collectora dla trace’ów z checkoutu. Pipeline wykonuje kolejno:

1. odbiór OTLP po gRPC lub HTTP,
2. ochronę pamięci Collectora,
3. decyzję tail samplingową po zebraniu spanów,
4. batching,
5. eksport OTLP do backendu trace’ów oraz diagnostyczny exporter `debug`.

Polityki zachowują wszystkie trace’y oznaczone `ERROR`, trace’y trwające co
najmniej sekundę oraz pięcioprocentowy baseline pozostałego ruchu. Dlatego kod
musi ustawiać status spana, a nie tylko zapisywać tekst wyjątku w logu.

## Warunki poprawnego wdrożenia

- Wszystkie spany jednego trace’a muszą trafić do tej samej instancji procesora
  tail sampling. Przy wielu Collectorach potrzebny jest routing po trace ID.
- `decision_wait` musi uwzględniać typowy i skrajny czas trace’a. Zbyt mała
  wartość podejmie decyzję przed dotarciem późnych spanów.
- `num_traces`, limit pamięci i `expected_new_traces_per_sec` muszą wynikać z
  pomiarów ruchu. Wartości w przykładzie nie są uniwersalnym sizingiem.
- Endpoint eksportera pochodzi ze zmiennej środowiskowej. Sekrety i tokeny nie
  powinny być commitowane w konfiguracji.
- Exporter `debug` jest dydaktyczny; przy dużym ruchu należy go wyłączyć.

Konfigurację trzeba zweryfikować poleceniem `otelcol validate --config ...` dla
konkretnej dystrybucji i wersji Collectora. Test repozytorium sprawdza kontrakt
edukacyjny konfiguracji, ale nie zastępuje walidatora binarnego Collectora.
