# virtual threads

<!-- material-card:start -->
> [!IMPORTANT]
> **Karta materiału**
> - **Zakres:** `fundament`
> - **Uczy:** virtual threads.
> - **Typowy błąd:** Uznanie pojedynczego wyniku dotyczącego „virtual threads” za gwarancję bez sprawdzenia niezmiennika i failure modes.
> - **Najkrótsza weryfikacja:** `.\mvnw.cmd --batch-mode --no-transfer-progress "-Dtest=VirtualThreadLifecycleTest,VirtualThreadWorkloadTest" test`
> - **Role klas:** `BoundedDownstream` = `correct`; `PinningDiagnosticDemo` = `simulation`.
> - **Granica:** Przykład dowodzi mechanizmu w opisanej granicy; bez testu infrastrukturalnego nie dowodzi zachowania wielu procesów ani konkretnej usługi.
<!-- material-card:end -->

## Virtual threads w Javie 21



Virtual thread jest lekkim wątkiem zarządzanym przez JVM. Pozwala zachować prosty,
blokujący styl kodu i uruchamiać bardzo wiele niezależnych operacji oczekujących
na I/O bez tworzenia takiej samej liczby kosztownych wątków systemowych. Nie jest
jednak szybszym procesorem, limitem połączeń do bazy ani mechanizmem anulowania.

## Mapa laboratorium

| Problem | Kod | Dowód wykonywalny |
|---|---|---|
| Platform threads kontra virtual threads przy blokowaniu | `BlockingConcurrencyComparison` | `VirtualThreadWorkloadTest` pokazuje kolejkę w ograniczonej puli i start wszystkich virtual threads |
| CPU-bound | `CpuBoundWorkload` | oba executory obliczają ten sam wynik; test nie formułuje niestabilnej tezy czasowej |
| Ograniczony downstream | `BoundedDownstream` | wiele virtual threads nie przekracza limitu `Semaphore` |
| Anulowanie | `CooperativeCancellation` | `Future.cancel(true)` przerywa oczekiwanie, a kod zachowuje flagę przerwania |
| Kontekst requestu | `RequestContextScope` | zwykły `ThreadLocal` nie przechodzi automatycznie do nowego virtual thread i jest sprzątany w `finally` |
| Pinning | `PinningExamples`, `PinningDiagnosticDemo` | porównanie blokowania wewnątrz i poza `synchronized` oraz osobny scenariusz diagnostyczny |

Uruchom testy pakietu z katalogu `backend-engineering`:

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress "-Dtest=VirtualThread*Test" test
```

## Blocking I/O: co naprawdę zmienia virtual thread

Ograniczona pula platform threads może uruchomić tylko tyle blokujących zadań,
ile ma workerów. Reszta czeka w kolejce executora. Executor tworzony przez
`Executors.newVirtualThreadPerTaskExecutor()` tworzy virtual thread dla każdego
zadania, więc oczekiwanie jednego requestu nie zajmuje osobnego platform thread
przez cały czas.

`BlockingConcurrencyComparison` używa bramek `CountDownLatch`, a nie czasu
wykonania. Dzięki temu test dowodzi różnicy w modelu współbieżności: przy puli
trzech platform threads przed zwolnieniem bramki startują trzy zadania, a przy
virtual threads mogą wystartować wszystkie. Nie dowodzi, że dowolna aplikacja
będzie szybsza — o tym decydują workload, JVM i downstream.

Virtual threads najlepiej pasują do dużej liczby niezależnych, blokujących
operacji I/O. Nie należy ich poolować tylko po to, by ograniczyć ich liczbę.
Jeśli ograniczenia wymaga konkretny zasób, ogranicz ten zasób bezpośrednio.

## CPU-bound: więcej wątków nie tworzy rdzeni

Kod CPU-bound przez większość czasu jest wykonywalny, a nie zaparkowany. Virtual
threads nadal korzystają z ograniczonej liczby carrier threads i fizycznych
rdzeni. Mogą uprościć strukturę programu, ale nie zwiększają mocy obliczeniowej.
Zbyt duża liczba zadań CPU-bound może jedynie zwiększyć konkurencję o CPU.

`CpuBoundWorkload` oddziela poprawność algorytmu od wydajności executora. Test
sprawdza identyczne wyniki dla obu modeli. Throughput, latency i alokacje należy
mierzyć w Stage 1B przez JMH oraz obserwować przez JFR; porównanie czasu w krótkim
teście jednostkowym byłoby podatne na warmup JIT i scheduler.

## Downstream jest rzeczywistym limitem

Tysiące tanich wątków nie dają tysiąca połączeń do PostgreSQL ani większego
limitu zewnętrznego API. Jeśli baza obsługuje jednocześnie 20 operacji, nadmiar
żądań musi czekać albo zostać odrzucony. `BoundedDownstream` modeluje tę granicę
przez sprawiedliwy `Semaphore` i zawsze zwalnia permit w `finally`.

To rozdziela dwie decyzje:

- executor określa sposób uruchamiania zadań;
- bulkhead, pula połączeń lub `Semaphore` chroni ograniczony zasób.

Z prawa Little'a wynika, że oczekiwana współbieżność jest w przybliżeniu iloczynem
throughputu i czasu przebywania requestu w systemie. Gdy zwiększymy liczbę
requestów ponad przepustowość downstreamu, rośnie przede wszystkim kolejka i
latency, a nie użyteczny throughput. Potrzebne są więc także timeout, limit
kolejki i kontrolowana reakcja na przeciążenie.

## Anulowanie i przerwania

`Future.cancel(true)` wysyła żądanie przerwania. Nie zabija bezwarunkowo zadania:
kod musi dojść do operacji reagującej na interrupt albo jawnie sprawdzać flagę.
Po złapaniu `InterruptedException` należy zazwyczaj zakończyć bieżącą operację
lub przekazać wyjątek wyżej. Jeżeli sygnatura nie pozwala go przekazać,
`CooperativeCancellation` przywraca flagę przez `Thread.currentThread().interrupt()`.

Połknięcie `InterruptedException` sprawia, że timeout lub zamknięcie executora
może nie zatrzymać pracy. Virtual threads obniżają koszt oczekiwania, lecz nie
zmieniają kooperacyjnego kontraktu anulowania.

## `ThreadLocal` i kontekst requestu

Zwykły `ThreadLocal` nie jest automatycznie dziedziczony przez nowy virtual
thread. `RequestContextScope` pokazuje jawne zainstalowanie kontekstu na czas
operacji i bezwarunkowe `remove()` w `finally`. Jest to ważne również przy
platform threads: kontekst ukryty w pamięci lokalnej wątku utrudnia śledzenie
przepływu i może wyciec do kolejnej operacji, jeśli nie zostanie usunięty.

W Javie 21 `ScopedValue` jest API preview. Może lepiej wyrażać niemutowalny,
ograniczony leksykalnie kontekst, ale jego przykład wymagałby włączenia preview
zarówno w kompilatorze, jak i podczas uruchomienia. To laboratorium pozostaje na
stabilnych API i pokazuje obowiązki, które łatwo przeoczyć przy `ThreadLocal`.

## Pinning w Javie 21

Virtual thread zwykle odmontowuje się od carrier thread podczas blokującego I/O.
W JDK 21 nie może tego zrobić, gdy blokuje się wewnątrz `synchronized` albo
niektórych operacji native/foreign. Carrier pozostaje wtedy zajęty — jest to
pinning. Krótka sekcja `synchronized` nie jest sama w sobie błędem; problemem
jest długie lub częste blokowanie I/O podczas posiadania monitora.

`PinningExamples` zestawia dwie struktury kodu: operację blokującą pod monitorem
oraz krótką aktualizację stanu przed i po I/O. Druga wersja nie trzyma monitora
podczas oczekiwania. Diagnostykę można uruchomić po kompilacji:

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress -DskipTests compile
java '-Djdk.tracePinnedThreads=full' -cp target/classes pl.jakubtworek.backend_engineering.stage_1.block_a.virtual_threads.PinningDiagnosticDemo
```

W dłuższym nagraniu JFR należy szukać zdarzenia `jdk.VirtualThreadPinned` i
łączyć je ze stosem wywołań oraz symptomami aplikacji. Sam fakt użycia
`synchronized` nie uzasadnia mechanicznego zastępowania wszystkich monitorów.

## Granice przykładu

Laboratorium nie symuluje prawdziwej bazy, sieci ani produkcyjnego obciążenia.
Latch i `Semaphore` służą do deterministycznego pokazania kontraktów. Przed
decyzją produkcyjną trzeba zmierzyć throughput, percentyle latency, liczbę
aktywnych requestów, kolejki, wykorzystanie puli połączeń, CPU i zdarzenia JFR.
