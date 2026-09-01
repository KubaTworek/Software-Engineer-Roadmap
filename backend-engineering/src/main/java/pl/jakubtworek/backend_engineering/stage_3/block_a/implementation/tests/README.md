# tests

<!-- material-card:start -->
> [!IMPORTANT]
> **Karta materiału**
> - **Zakres:** `temat-zaawansowany`
> - **Uczy:** tests.
> - **Typowy błąd:** Uznanie pojedynczego wyniku dotyczącego „tests” za gwarancję bez sprawdzenia niezmiennika i failure modes.
> - **Najkrótsza weryfikacja:** `.\mvnw.cmd --batch-mode --no-transfer-progress test`
> - **Role klas:** brak klasy-kontrprzykładu; pozostałe typy są minimalnymi modelami pojęć opisanych niżej.
> - **Granica:** Model weryfikuje nazwany niezmiennik; nie implementuje produkcyjnego protokołu rozproszonego ani infrastruktury dostawcy.
<!-- material-card:end -->

## Wydajność całego systemu: od capacity do eksperymentu



## Cel laboratorium

Mikrobenchmark odpowiada na pytanie o koszt małego fragmentu kodu w kontrolowanym
środowisku. Test całej usługi sprawdza zachowanie kolejek, pul połączeń, bazy, cache,
sieci, autoscalera i mechanizmów degradacji pod rzeczywistym profilem ruchu. Wyników
JMH nie wolno przeliczać wprost na maksymalny RPS systemu.

Ten pakiet koduje przepływ:

```text
hipoteza capacity i pierwszy bottleneck
  → workload model oraz scenariusz
  → SLO i twarde guardrails zapisane przed testem
  → obserwacje RPS, p95, p99, errors, saturation i queue depth
  → kontrolowana degradacja lub bezpieczny abort
  → ocena autoskalowania i aktualizacja modelu
```

Kod nie generuje prawdziwego ruchu. Jest kontraktem, który można podłączyć do k6,
Gatlinga, JMetera lub platformy wewnętrznej oraz do metryk środowiska testowego.

## Rodzaje testów

| Typ | Pytanie | Typowy profil | Czego nie należy z niego wnioskować |
| --- | --- | --- | --- |
| load | czy przewidywany ruch mieści się w SLO z zapasem | stabilny ruch poniżej `safeRps` | maksymalnej pojemności |
| stress / step | gdzie znajduje się knee i pierwszy bottleneck | kolejne poziomy aż ponad modelowany limit | zachowania długoterminowego |
| spike | czy system przeżywa nagły skok i opóźnienie autoscalera | natychmiastowy wzrost offered load | że reaktywny autoscaling zawsze zdąży |
| soak | czy stan degraduje się w czasie | długi ruch blisko wartości operacyjnej | limitu chwilowego burstu |

Soak test obserwuje m.in. heap po pełnym GC, liczbę wątków i deskryptorów, pule,
backlog, tempo błędów, cache hit ratio oraz dryf p95/p99. „Dwie godziny bez crasha” nie
jest wystarczającym kryterium, jeżeli kolejka albo pamięć stale rosną.

`LoadTestType` zachowuje starsze nazwy `BASELINE` i `STEP`, a `LOAD` oraz `STRESS`
nazywają intencję wprost. `LoadTestRunner` nadal tylko tworzy deterministyczny
harmonogram kroków. Pełny kontrakt eksperymentu reprezentują
`PerformanceExperimentPlan` i `PerformanceExperimentRunner`.

## Capacity ustalane przed testem

`CapacityHypothesis.from` korzysta z kanonicznego `CapacityPlan` i
`BottleneckAnalyzer`. Najpierw porównuje ograniczenie CPU API, puli zależności oraz
zapisów DB, a potem odejmuje jawny headroom:

```text
safeRps = pierwszy_modelowany_limit × (1 - headroom)
```

Hipoteza powinna zawierać wersję aplikacji, dataset, liczbę replik, rozmiary maszyn,
konfigurację JVM, pule, cache hit ratio, request mix i przewidywany bottleneck.
Headroom pokrywa burst, nierówny routing, awarię części replik, retry i błąd modelu.
Nie jest „stratą” pojemności.

Model jest punktem startowym. Jeśli test wskazuje inny pierwszy bottleneck, wynik nie
jest porażką testu — należy zaktualizować założenia oraz wyjaśnić brakującą zmienną.

## Open model kontra closed model

`WorkloadProfile` zabrania mieszania dwóch semantyk:

- **open model** planuje przyjścia niezależnie od czasu odpowiedzi, np. 500 RPS;
- **closed model** utrzymuje określoną liczbę użytkowników, a kolejna operacja zaczyna
  się po zakończeniu poprzedniej i ewentualnym think time.

Closed model dobrze reprezentuje zamkniętą populację sesji, ale przy wzroście latencji
sam zmniejsza tempo nowych requestów. Może więc ukryć przeciążenie systemu, którego
rzeczywisty ruch nie zwalnia. Open model lepiej sprawdza capacity API o zewnętrznie
narzuconym arrival rate, lecz generator musi mieć własny zapas CPU, sieci i połączeń.

Do wyniku zawsze zapisujemy **offered RPS** i **achieved RPS**. Sam achieved throughput
może wyglądać stabilnie dokładnie dlatego, że generator lub system nie zdołał wysłać
planowanego ruchu.

## Coordinated omission

Jeżeli generator wysyła kolejny request dopiero po zakończeniu poprzedniego, w czasie
zawieszenia usługi nie tworzy próbek dla requestów, które powinny były nadejść.
Histogram wygląda wtedy lepiej niż doświadczenie użytkowników.

`LatencyMeasurement` rozróżnia:

```text
observed latency          = completedAt - startedAt
schedule-corrected latency = completedAt - scheduledAt
```

Test pokazuje request zaplanowany na `t=0`, uruchomiony dopiero przy `t=900 ms` i
zakończony przy `t=1000 ms`. Naiwny pomiar raportuje 100 ms, a pomiar uwzględniający
opóźnienie harmonogramu — 1000 ms. W praktyce używaj generatora i histogramu, które
jawnie wspierają korektę coordinated omission; nie próbuj poprawiać wyniku samą
średnią.

## Obserwacje i kryteria sukcesu

`ExperimentObservation` zapisuje dla każdego okna:

- offered i achieved RPS,
- p95 oraz p99 zamiast wyłącznie średniej,
- error rate i load-shed rate,
- saturację aplikacji oraz osobno zależności,
- queue depth i liczbę gotowych replik.

`PerformanceObjectives` definiuje SLO oraz minimalny stosunek achieved/offered.
`PerformanceExperimentEvaluator` nie uznaje testu za udany tylko dlatego, że CPU jest
niski. P99, throughput albo dependency saturation mogą już wskazywać porażkę.

Percentyle należy liczyć z histogramu o poprawnych bucketach i agregować z pełnego
rozkładu. Średnia z p99 poszczególnych instancji nie jest globalnym p99. Wyniki z
okresu warm-up, rolloutów, cold startu i steady state trzeba oznaczać osobno.

## Kontrolowana degradacja

Stress test powyżej modelowanego limitu nie musi zwrócić sukcesu dla każdego requestu.
Powinien natomiast pokazać przewidywalną degradację:

- bounded queue nie rośnie bez końca,
- load shedding zaczyna działać przed wyczerpaniem wszystkich zasobów,
- błędy są jawne i klasyfikowalne,
- zdrowe ścieżki zachowują swój bulkhead,
- system wraca do SLO po zmniejszeniu ruchu bez restartu.

`controlledDegradation` wymaga ruchu ponad modelowany limit, widocznego load shedding
i braku przekroczenia twardych guardrails. Brak błędów przy rosnącej bez końca kolejce
nie jest sukcesem — tylko odłożeniem awarii.

## Weryfikacja autoskalowania

Samo zwiększenie `replicas` nie dowodzi skuteczności HPA. Test zapisuje trzy okna:

1. baseline przed wzrostem ruchu,
2. pressure wraz z czasem detekcji i uruchamiania replik,
3. recovered po osiągnięciu readiness i rozgrzaniu instancji.

`assessAutoscaling` uznaje skalowanie za skuteczne dopiero, gdy repliki rzeczywiście
wzrosły, achieved throughput podąża za offered load, a p95/p99 i error rate wracają do
SLO. Jeśli nasycona jest baza lub inna zależność, dodawanie API jest oznaczone jako
`UNSAFE`, ponieważ może zwiększyć liczbę połączeń i pogorszyć awarię.

Warto osobno mierzyć czas: signal → decyzja HPA → scheduling → readiness → realne
przejęcie ruchu. Spike krótszy od tej ścieżki musi być obsłużony headroomem, kolejką
ograniczoną i load sheddingiem, a nie obietnicą autoscalera.

## Kryteria przerwania eksperymentu

SLO opisuje akceptowalny wynik. Guardrail chroni środowisko oraz zależności i zatrzymuje
test natychmiast. `ExperimentGuardrails` kontroluje:

- krytyczne p99,
- maksymalny error rate,
- saturację aplikacji lub zależności,
- queue depth.

W realnym planie należy dodać także koszt chmury, replication lag, brak miejsca na
dysku, wpływ na współdzielone tenanty, błędy integralności danych i ręczny stop osoby
prowadzącej eksperyment. Kryteria zapisuje się **przed** testem. Nie podnosi się ich w
trakcie tylko dlatego, że wykres wygląda interesująco.

`PerformanceExperimentRunner` sprawdza guardrails po każdym oknie i nie uruchamia
następnej fazy po przekroczeniu progu. Narzędzie produkcyjne powinno robić to również
w trakcie fazy, np. co kilka sekund, oraz wykonać kontrolowany ramp-down i zebrać dane
diagnostyczne.

## Protokół eksperymentu

1. Zapisz pytanie, hipotezę capacity i oczekiwany pierwszy bottleneck.
2. Zamroź wersję kodu, konfigurację, dataset i topologię środowiska.
3. Wybierz open lub closed model zgodnie z zachowaniem użytkowników.
4. Zdefiniuj request mix, dane testowe, SLO, guardrails i warunek recovery.
5. Potwierdź, że generator nie jest bottleneckiem i synchronizacja czasu działa.
6. Wykonaj warm-up, load, stress/spike oraz recovery; soak uruchom osobno.
7. Koreluj RPS, percentyle i błędy z saturation każdej zależności.
8. Porównaj wynik z modelem, opisz knee i zaktualizuj capacity plan.
9. Zachowaj konfigurację i wnioski, nie „magiczne” wyniki bez kontekstu.

Nie uruchamiaj stress ani spike testów przeciwko produkcji bez jawnej zgody,
izolacji blast radius, przygotowanych guardrails i właścicieli zależności.

## Wykonywalne testy modelu

| Test | Co sprawdza |
| --- | --- |
| `LoadTestRunnerTest` | harmonogram step zachowuje target i całkowity czas |
| `SystemPerformanceMethodologyTest` | capacity przed testem, rozłączność open/closed i coordinated omission |
| `PerformanceExperimentEvaluatorTest` | p99, saturation, degradację i bezpieczne autoskalowanie |
| `PerformanceExperimentRunnerTest` | przekroczenie guardraila zatrzymuje kolejne fazy |

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress "-Dtest=LoadTestRunnerTest,SystemPerformanceMethodologyTest,PerformanceExperimentEvaluatorTest,PerformanceExperimentRunnerTest" test
```

## Granice przykładu

- Nie ma adaptera do konkretnego generatora ani backendu metryk.
- Percentyle są wejściem do modelu, nie implementacją produkcyjnego histogramu.
- Testy jednostkowe dowodzą reguł eksperymentu, a nie capacity tej aplikacji.
- Model pierwszego rzędu nie uwzględnia wszystkich kolejek, locków, GC, TLS,
  nierównego shardingu ani coordinated failure kilku zależności.
- Wartości w testach są danymi scenariusza, nie uniwersalnymi progami produkcyjnymi.

Powiązane materiały: [metodologia JMH i diagnostyka JVM](../../../../stage_1/block_b/README.md),
[kontrola przeciążenia](../overload/README.md),
[metryki Stage 3A](../../metrics/README.md) oraz
[pipeline observability](../../../block_b/pipeline/README.md).
