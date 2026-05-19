# Case 14 — Thread count tuning: ile wątków ma sens

## Wprowadzenie

Jednym z najbardziej klasycznych pytań wydajnościowych w systemach współbieżnych jest:

> „Ile wątków powinna mieć aplikacja?”

Bardzo często odpowiedź brzmi:
> „to zależy”.

I nie jest to wymijająca odpowiedź.

Liczba sensownych wątków zależy przede wszystkim od:
- rodzaju workloadu,
- charakterystyki CPU,
- ilości waiting,
- contention,
- I/O,
- kosztu schedulingu,
- kosztu context switching.

To oznacza:
- nie istnieje jedna „magiczna liczba”.

I właśnie dlatego:
> tuning liczby wątków jest problemem skalowania systemu, a nie prostą konfiguracją.

---

# Wątek nie oznacza równoległego wykonania

To podstawowa intuicja.

Wiele osób zakłada:
- więcej wątków = większa wydajność.

To nie zawsze prawda.

CPU posiada:
- ograniczoną liczbę rdzeni,
- ograniczoną przepustowość cache,
- ograniczoną możliwość wykonywania instrukcji równolegle.

Jeżeli:
- liczba aktywnie pracujących wątków
  przekracza możliwości CPU,

to:
- system zaczyna przełączać kontekst,
- scheduler OS wykonuje dodatkową pracę,
- throughput może przestać rosnąć,
- a nawet spaść.

---

# CPU-bound workload

CPU-bound oznacza:
- wątki aktywnie wykonują instrukcje CPU,
- bottleneckiem jest moc obliczeniowa procesora.

Typowe przykłady:
- kompresja,
- parsowanie,
- kryptografia,
- analiza danych,
- algorytmy numeryczne.

W takim workloadzie:
> CPU jest stale zajęte pracą.

---

# Dlaczego CPU-bound nie skaluje się w nieskończoność

Jeżeli masz:
- 8 rdzeni CPU,
- i workload czysto CPU-bound,

to:
- 8 aktywnych wątków
  często daje bardzo dobre wykorzystanie CPU.

Ale:
- 64 aktywne wątki
  nie sprawią magicznie,
  że CPU stanie się 8 razy szybsze.

Zamiast tego pojawiają się:
- context switching,
- scheduler overhead,
- cache pollution,
- contention o zasoby CPU.

---

# Context switching

To jeden z głównych kosztów nadmiaru wątków.

Kiedy OS przełącza wątki:
- zapisuje stan jednego wątku,
- przywraca stan drugiego,
- zmienia working set CPU,
- zaburza locality cache.

To:
- nie wykonuje użytecznej pracy biznesowej,
- ale kosztuje czas CPU.

Przy bardzo dużej liczbie runnable threads:
- scheduler może zacząć dominować runtime.

---

# Cache locality i nadmiar wątków

Każdy wątek:
- posiada własny working set danych,
- korzysta z cache CPU.

Im więcej aktywnych wątków:
- tym większy pressure na cache,
- tym częstsze eviction danych,
- tym gorsza locality.

To oznacza:
- więcej cache misses,
- więcej dostępu do RAM,
- niższą efektywność CPU.

---

# Wait-bound workload

Wait-bound oznacza:
- wątki przez większość czasu czekają,
- CPU nie jest stale zajęte.

Przykłady:
- requesty HTTP,
- database queries,
- filesystem I/O,
- sleep,
- waiting on locks,
- external APIs.

Tutaj sytuacja wygląda zupełnie inaczej.

---

# Dlaczego więcej wątków pomaga przy waiting

Jeżeli:
- część wątków czeka,
- CPU jest częściowo bezczynne,

to dodatkowe wątki:
- mogą wykonywać pracę w czasie oczekiwania innych.

Dlatego:
> workload wait-bound może korzystać z liczby wątków znacznie większej niż liczba rdzeni.

To bardzo częsty przypadek:
- serwerów webowych,
- systemów integracyjnych,
- backendów I/O-heavy.

---

# Little’s Law i concurrency

W praktyce:
- liczba sensownych wątków
  zależy od:
    - czasu CPU,
    - czasu waiting,
    - throughput target.

Jeżeli request:
- używa CPU przez 5 ms,
- a czeka na I/O przez 95 ms,

to:
- CPU przez większość czasu jest wolne.

Wtedy:
- więcej wątków może poprawić throughput.

---

# Mixed workload — najczęstszy przypadek

Większość systemów produkcyjnych:
- nie jest ani czysto CPU-bound,
- ani czysto wait-bound.

To zwykle:
## mixed workload

czyli:
- trochę CPU,
- trochę I/O,
- trochę lock contention,
- trochę serialization,
- trochę GC.

I właśnie dlatego:
- tuning thread count
  w praktyce wymaga eksperymentów i profilowania.

---

# Oversubscription

Oversubscription oznacza:
- więcej aktywnych runnable threads niż CPU jest w stanie efektywnie obsłużyć.

Objawy:
- wysoki context switching,
- rosnące latency,
- spadek throughput,
- scheduler pressure,
- cache thrashing.

To bardzo częsty problem:
- „więcej wątków”
  zaczyna pogarszać wydajność.

---

# Throughput collapse

W pewnym momencie:
- dodawanie kolejnych wątków
  przestaje pomagać.

Następnie:
- throughput stabilizuje się,
- a później może zacząć spadać.

To właśnie:
## throughput collapse

Powód:
- scheduler overhead,
- contention,
- cache pressure,
- synchronization cost.

---

# Thread pools — dlaczego są konieczne

Tworzenie:
- nieograniczonej liczby wątków
  jest bardzo niebezpieczne.

Dlatego używa się:
## thread pools

które:
- ograniczają concurrency,
- kontrolują queueing,
- stabilizują resource usage.

Ale:
- nawet thread pool wymaga poprawnego sizingu.

---

# Queueing

Zbyt mało wątków:
- powoduje kolejki pracy,
- zwiększa waiting latency.

Zbyt dużo:
- powoduje oversubscription.

Czyli:
> tuning concurrency zawsze jest kompromisem.

---

# Hyper-threading i logical cores

Nowoczesne CPU:
- często pokazują więcej logical cores niż physical cores.

Ale:
- logical core ≠ pełny dodatkowy rdzeń.

Dlatego:
- workload CPU-bound
  nie zawsze skaluje się liniowo do liczby logical CPUs.

---

# Virtual threads — ważne rozróżnienie

W nowoczesnej Javie pojawiają się:
## virtual threads

To zmienia:
- koszt blokowania,
- koszt schedulingu,
- model concurrency.

Ale:
- nie usuwa ograniczeń CPU.

Nawet przy virtual threads:
- workload CPU-bound nadal jest ograniczony przez fizyczne rdzenie.

---

# Dlaczego eksperymenty są konieczne

Nie istnieje uniwersalna formuła:

```text
idealThreadCount = X
```

Ponieważ wszystko zależy od:
- workloadu,
- CPU,
- I/O,
- contention,
- GC,
- scheduler behavior.

Dlatego:
> tuning thread count powinien być empiryczny.

---

# Co obserwować podczas eksperymentów

Najważniejsze metryki:
- throughput,
- latency,
- CPU usage,
- runnable threads,
- context switching,
- queue sizes,
- thread states.

To właśnie te dane pokazują:
- czy system jest CPU-bound,
- czy wait-bound,
- czy oversubscribed.

---

# Najczęstszy błąd tuningowy

Najczęstszy błąd wygląda tak:

> „System jest wolny, dodajmy więcej wątków”

Podczas gdy:
- CPU już jest przeciążone,
- scheduler cierpi,
- context switching dominuje runtime.

Wtedy:
- więcej wątków
  pogarsza sytuację.

---

# Najważniejsza intuicja praktyczna

Najbardziej praktyczny wniosek brzmi:

> optymalna liczba wątków zależy od proporcji między aktywną pracą CPU a czasem oczekiwania.

I właśnie dlatego:
- concurrency tuning
  jest problemem skalowania całego systemu,
  a nie tylko ustawienia thread poola.

---

# Najważniejsze wnioski

Najbardziej użyteczny model mentalny wygląda tak:

- więcej wątków nie oznacza automatycznie większej wydajności,
- workload CPU-bound zwykle skaluje się do liczby rdzeni,
- workload wait-bound może korzystać z większej liczby wątków,
- nadmiar runnable threads powoduje context switching overhead,
- scheduler i cache locality mają ogromny wpływ na throughput,
- oversubscription może prowadzić do throughput collapse,
- thread pools pomagają kontrolować concurrency,
- tuning liczby wątków jest kompromisem między queueing i scheduling cost,
- mixed workloads wymagają empirycznego strojenia,
- poprawny tuning concurrency wymaga obserwacji throughput, latency i thread states.