# Case 11 — Naive benchmark vs JMH: jak nie okłamywać się wynikami

## Wprowadzenie

Benchmarking wydaje się prosty.

Wystarczy przecież:

```java
long start = System.nanoTime();

/* kod */

long end = System.nanoTime();
```

i już „wiemy”, co jest szybsze.

W praktyce jednak:
> benchmarkowanie na JVM jest jednym z najbardziej zdradliwych obszarów performance engineering.

Dlaczego?

Ponieważ nowoczesny JVM:
- dynamicznie kompiluje kod,
- profiluje runtime,
- inline’uje metody,
- eliminuje martwy kod,
- wykonuje escape analysis,
- optymalizuje constant expressions,
- zmienia zachowanie programu po warmupie.

To oznacza:
- benchmark może mierzyć coś zupełnie innego niż myślimy.

I właśnie dlatego powstał:
## JMH — Java Microbenchmark Harness

oficjalny framework benchmarkowy OpenJDK.

---

# Dlaczego benchmarkowanie JVM jest trudne

W językach natywnych:
- kod zwykle jest skompilowany wcześniej,
- zachowanie runtime jest bardziej stabilne.

Natomiast JVM:
- optymalizuje program dynamicznie,
- na podstawie rzeczywistego wykonania.

To oznacza:
- pierwszy przebieg programu może wyglądać zupełnie inaczej niż kolejny,
- JVM stale zmienia strategię wykonania.

W efekcie:
> benchmark JVM bez kontroli runtime bardzo łatwo staje się bezwartościowy.

---

# Warmup — najczęstszy problem

JVM nie startuje od razu z pełną optymalizacją.

Kod przechodzi zwykle przez:
- interpretację,
- kompilację C1,
- profilowanie,
- kompilację C2,
- kolejne recompilations.

To oznacza:
- pierwsze sekundy działania aplikacji
  nie reprezentują stabilnej wydajności.

Dlatego:
> benchmark bez warmupu mierzy głównie proces rozgrzewania JVM.

---

# Dlaczego `System.nanoTime()` jest niewystarczające

Sam pomiar czasu:
- nie rozwiązuje problemów metodologicznych.

Benchmark typu:

```java
long start = System.nanoTime();
```

nie kontroluje:
- warmupu,
- JIT compilation,
- dead-code elimination,
- fork isolation,
- GC interference,
- statistical noise,
- thermal throttling,
- OS scheduling.

W efekcie:
- wyniki często są przypadkowe,
- niestabilne,
- albo całkowicie błędne.

---

# Dead-code elimination

To jeden z najbardziej klasycznych problemów.

Jeżeli wynik obliczenia:
- nie jest używany,
- nie wpływa na observable behavior,

JIT może:
- usunąć część kodu,
- albo cały benchmark.

Przykład:

```java
for (...) {
    compute();
}
```

jeżeli rezultat `compute()`:
- nie jest nigdzie wykorzystywany,

to benchmark może mierzyć:
> prawie nic.

I właśnie dlatego benchmark:
- „działa niesamowicie szybko”.

---

# Constant folding

Kolejny bardzo częsty problem.

Jeżeli benchmark stale wykonuje:

```java
compute(42)
```

to JVM może:
- obliczyć wynik wcześniej,
- uprościć wyrażenie,
- zoptymalizować kod w sposób nierealistyczny.

Wtedy benchmark:
- nie reprezentuje prawdziwego workloadu,
- mierzy artefakty optymalizacji.

---

# Inlining i agresywne optymalizacje

Małe metody:
- są idealnymi kandydatami do inliningu.

Po inliningu JVM może:
- propagować stałe,
- usuwać gałęzie,
- upraszczać obliczenia,
- eliminować alokacje.

W efekcie:
- benchmark bardzo często nie mierzy już oryginalnego kodu,
- lecz mocno przekształcony graph optymalizacyjny.

To nie jest „błąd JVM”.

To naturalne zachowanie nowoczesnego JIT.

---

# Dlaczego benchmark musi być izolowany

Wydajność JVM jest silnie zależna od:
- aktualnego stanu procesu,
- poprzednich benchmarków,
- GC history,
- profilu runtime,
- layoutu pamięci.

Dlatego benchmarki:
- powinny być uruchamiane w osobnych forkach JVM.

Inaczej:
- jeden benchmark może wpływać na drugi,
- wyniki stają się niestabilne.

---

# JMH — po co naprawdę istnieje

JMH został stworzony właśnie po to, by:
- kontrolować zachowanie JVM,
- ograniczać błędy benchmarkowe,
- zapewniać powtarzalność wyników.

To nie jest „ładniejszy timer”.

To:
## harness metodologiczny

dla benchmarków JVM.

---

# Co robi JMH

JMH automatycznie:
- wykonuje warmup,
- rozdziela measurement phase,
- uruchamia benchmarki w forkach,
- ogranicza dead-code elimination,
- dostarcza Blackhole,
- kontroluje iteration lifecycle,
- stabilizuje pomiary,
- generuje statystyki.

To ogromna różnica względem:
- prostego `nanoTime()`.

---

# Blackhole

To bardzo ważny mechanizm.

`Blackhole`:
- konsumuje wynik benchmarku,
- sprawia, że rezultat pozostaje observable,
- utrudnia JVM usunięcie kodu.

Bez tego:
- benchmark może zostać częściowo zoptymalizowany away.

---

# Forks

JMH uruchamia benchmark:
- w osobnych JVM processach.

Dlaczego?

Ponieważ:
- JVM adaptuje się runtime,
- benchmarki wpływają na siebie,
- profilowanie jednego testu może zmieniać drugi.

Forki poprawiają:
- izolację,
- powtarzalność,
- wiarygodność wyników.

---

# Throughput vs AverageTime

JMH pozwala mierzyć różne metryki.

Najczęstsze:
- Throughput,
- AverageTime,
- SampleTime.

To bardzo ważne, bo:
- różne workloady wymagają różnych interpretacji.

Przykład:
- throughput mówi:
    - ile operacji wykonujemy,
- latency mówi:
    - jak długo trwa pojedyncza operacja.

---

# Dlaczego benchmarki micro są zdradliwe

Microbenchmark:
- mierzy bardzo mały fragment kodu.

To oznacza:
- JVM może zoptymalizować go agresywniej niż w realnym systemie,
- locality danych może być nierealistyczna,
- branch prediction może być sztucznie idealne.

Dlatego:
> dobry microbenchmark nie gwarantuje dobrego zachowania produkcyjnego.

---

# Benchmark ≠ profiling

To bardzo ważne rozróżnienie.

Benchmark:
- mierzy wydajność konkretnej operacji.

Profiler:
- pokazuje gdzie system traci czas.

To różne narzędzia do różnych problemów.

Bardzo częsty błąd:
- optymalizowanie benchmarku,
- zamiast rzeczywistego bottlenecku produkcyjnego.

---

# Benchmarki bez metodologii są niebezpieczne

Największy problem benchmarków nie polega na:
- niewielkiej niedokładności.

Problem polega na tym, że:
> benchmark może dawać całkowicie błędne wnioski.

I właśnie dlatego:
- wiele internetowych „performance comparisons”
  jest metodologicznie bezwartościowych.

---

# Dlaczego wyniki benchmarków bywają niestabilne

Na wyniki wpływają:
- JIT state,
- GC,
- OS scheduler,
- thermal throttling,
- CPU frequency scaling,
- NUMA,
- background processes,
- cache locality.

To oznacza:
- pojedynczy wynik benchmarku praktycznie nic nie znaczy.

Potrzebna jest:
- statystyka,
- wiele iteracji,
- powtarzalność.

---

# Najczęstszy błąd początkujących

Najczęstszy błąd wygląda tak:

> „Napisałem benchmark i kod A jest 10x szybszy”

podczas gdy benchmark:
- mierzy dead-code elimination,
- constant folding,
- warmup,
- albo artefakt JIT.

To prowadzi do:
- błędnych decyzji architektonicznych,
- overengineeringu,
- pseudooptymalizacji.

---

# Najważniejsza intuicja praktyczna

Najbardziej praktyczny wniosek brzmi:

> benchmark JVM bez poprawnej metodologii bardzo łatwo mierzy zachowanie JIT, a nie rzeczywisty koszt kodu.

I właśnie dlatego:
- JMH jest fundamentem wiarygodnego benchmarkowania JVM.

---

# Najważniejsze wnioski

Najbardziej użyteczny model mentalny wygląda tak:

- JVM dynamicznie optymalizuje kod runtime,
- benchmark bez warmupu jest niewiarygodny,
- dead-code elimination może usunąć benchmarkowany kod,
- constant folding może sztucznie zawyżać wyniki,
- inlining zmienia rzeczywisty koszt wykonania,
- benchmarki powinny być izolowane w osobnych forkach,
- JMH kontroluje metodologię benchmarków JVM,
- `Blackhole` pomaga zapobiegać eliminacji kodu,
- pojedynczy wynik benchmarku jest mało wartościowy,
- benchmarking wymaga statystyki, warmupu i poprawnej interpretacji wyników.