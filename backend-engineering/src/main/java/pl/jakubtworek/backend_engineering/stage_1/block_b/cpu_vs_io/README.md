# Case 10 — CPU hotspot vs I/O bottleneck: rozdziel CPU-bound od wait-bound

## Wprowadzenie

Jednym z najczęstszych błędów podczas profilowania aplikacji JVM jest utożsamianie:
- wysokiego czasu odpowiedzi,
- wysokiego wall-clock time,
- „wolnego requestu”

z:
- wysokim użyciem CPU.

To bardzo często jest błędne założenie.

Aplikacja może być „wolna”, ponieważ:
- intensywnie obciąża CPU,
- albo dlatego, że głównie czeka.

I właśnie rozróżnienie:
- CPU-bound,
- oraz wait-bound / I/O-bound

jest jednym z fundamentów poprawnej interpretacji profilowania.

---

# CPU-bound vs wait-bound

To dwa zupełnie różne typy workloadów.

## CPU-bound

oznacza:
- aplikacja aktywnie wykonuje instrukcje CPU,
- większość czasu spędzana jest na obliczeniach,
- bottleneckiem jest moc obliczeniowa procesora.

Typowe przykłady:
- kompresja,
- kryptografia,
- parsowanie,
- analiza danych,
- algorytmy numeryczne.

---

## Wait-bound / I/O-bound

oznacza:
- aplikacja głównie czeka,
- CPU przez większość czasu nic nie robi,
- bottleneckiem jest zewnętrzny system lub synchronizacja.

Typowe przykłady:
- requesty HTTP,
- database queries,
- filesystem I/O,
- sleep/wait/park,
- lock contention,
- kolejki i messaging.

---

# Dlaczego wall-clock time jest mylący

To kluczowy problem.

Jeżeli metoda wykonuje się:

```text
500 ms
```

to:
- nie oznacza automatycznie,
- że przez 500 ms zużywa CPU.

Możliwe scenariusze:
- 495 ms waiting + 5 ms CPU,
- albo:
- 500 ms pełnego wykorzystania CPU.

Dla użytkownika:
- oba requesty są „wolne”.

Ale przyczyna problemu jest całkowicie inna.

---

# CPU samples — co naprawdę mierzy profiler

JFR i większość profilerów CPU używa:
## sampling profiler

Profiler okresowo sprawdza:
- co aktualnie wykonuje CPU,
- gdzie znajdują się aktywne wątki,
- jakie metody są na stack trace.

To oznacza:
> CPU profiling pokazuje, gdzie JVM zużywa CPU, a nie gdzie upływa wall-clock time.

To fundamentalna różnica.

---

# Dlaczego wait-bound workload nie pojawia się jako hotspot CPU

Jeżeli wątek wykonuje:

```java
Thread.sleep()
```

to:
- wall-clock time płynie,
- ale CPU praktycznie nic nie robi.

W profilerze CPU:
- taki kod prawie nie pojawia się jako hotspot.

Zamiast tego:
- wątek będzie widoczny jako:
    - TIMED_WAITING,
    - PARKED,
    - BLOCKED,
    - WAITING.

I właśnie dlatego:
> analiza samych hotspotów CPU bywa niewystarczająca.

---

# Thread states — klucz do interpretacji

W JVM wątki mogą znajdować się w różnych stanach:
- RUNNABLE,
- BLOCKED,
- WAITING,
- TIMED_WAITING.

To niezwykle ważne przy interpretacji profilu.

## RUNNABLE

oznacza zwykle:
- wątek jest gotowy do wykonania,
- albo aktywnie zużywa CPU.

---

## WAITING / TIMED_WAITING

oznacza:
- wątek czeka,
- nie wykonuje aktywnej pracy CPU.

Typowe przyczyny:
- sleep,
- I/O,
- future.get(),
- locks,
- condition waits.

---

# CPU hotspot

Hotspot CPU to:
> fragment kodu, który realnie konsumuje czas procesora.

W JFR pojawia się jako:
- Hot Methods,
- Method Profiling,
- CPU Samples.

To zwykle miejsca:
- intensywnych obliczeń,
- tight loops,
- kosztownych algorytmów,
- contention spin loops,
- parsowania,
- serializacji.

---

# I/O bottleneck

I/O bottleneck wygląda zupełnie inaczej.

CPU często:
- jest prawie bezczynne,
- throughput jest niski,
- request latency jest wysoki.

Dlaczego?

Ponieważ:
- aplikacja czeka na system zewnętrzny.

Typowe przykłady:
- wolna baza danych,
- przeciążony storage,
- zewnętrzne API,
- sieć,
- DNS,
- lock contention.

---

# Dlaczego wysokie CPU nie zawsze oznacza problem

To kolejna ważna intuicja.

Czasem:
- wysokie CPU usage
  oznacza po prostu:
- aplikacja efektywnie wykonuje pracę.

Problem pojawia się wtedy, gdy:
- CPU jest wysokie,
- throughput nie rośnie,
- latency rośnie,
- profiler pokazuje nieefektywne hotspoty.

Np.:
- busy waiting,
- CAS contention,
- serialization overhead,
- GC pressure.

---

# Dlaczego niski CPU też może być problemem

To bardzo częsty przypadek produkcyjny.

System:
- ma wysokie latency,
- niski throughput,
- a CPU jest prawie bezczynne.

To zwykle oznacza:
- wait-bound bottleneck.

Czyli:
- CPU czeka,
- a problem leży poza procesorem.

Wtedy optymalizacja algorytmów CPU:
- praktycznie nic nie da.

---

# JFR — dlaczego jest idealny do tego case’u

JFR bardzo dobrze łączy:
- profiling CPU,
- stany wątków,
- lock contention,
- allocation profiling,
- GC events,
- wall-clock behavior.

To pozwala analizować:
- nie tylko „co jest wolne”,
- ale:
> dlaczego JVM realnie traci czas.

---

# JMC — interpretacja danych

W JDK Mission Control najważniejsze widoki dla tego case’u to:
- Hot Methods,
- Method Profiling,
- Thread States,
- Java Thread Statistics,
- CPU Load.

I właśnie połączenie tych danych daje poprawny obraz systemu.

Przykład:
- wysoki wall-clock,
- niski CPU,
- dużo WAITING

→ problem wait-bound.

Natomiast:
- wysoki CPU,
- RUNNABLE threads,
- hotspoty metod

→ problem CPU-bound.

---

# Mixed workloads — najtrudniejszy przypadek

W praktyce większość systemów jest:
## mixed workload

czyli:
- część czasu CPU,
- część czasu waiting,
- część czasu synchronization,
- część czasu I/O.

To właśnie dlatego:
- interpretacja profilowania wymaga korelacji danych,
- nie wystarczy patrzeć na jeden wykres.

---

# Dlaczego `Thread.sleep()` jest dobrym eksperymentem

`Thread.sleep()`:
- bardzo dobrze symuluje wait-bound behavior,
- nie zużywa CPU,
- generuje wall-clock latency.

To pozwala łatwo pokazać:
- różnicę między:
    - elapsed time,
    - a CPU time.

---

# Najczęstszy błąd profilowania

Najczęstszy błąd wygląda tak:

> „request trwa długo, więc trzeba optymalizować CPU”

Podczas gdy:
- CPU jest praktycznie bezczynne,
- a aplikacja czeka na:
    - bazę,
    - sieć,
    - lock,
    - storage,
    - zewnętrzne API.

To prowadzi do:
- optymalizowania niewłaściwego miejsca,
- marnowania czasu tuningowego.

---

# Najważniejsza intuicja praktyczna

Najbardziej praktyczny wniosek brzmi:

> czas ścienny i zużycie CPU to nie jest to samo.

I właśnie dlatego:
- poprawne profilowanie JVM wymaga rozdzielenia:
    - aktywnej pracy CPU,
    - od czasu oczekiwania.

---

# Najważniejsze wnioski

Najbardziej użyteczny model mentalny wygląda tak:

- CPU-bound workload aktywnie zużywa procesor,
- wait-bound workload głównie czeka,
- wall-clock time nie oznacza automatycznie wysokiego CPU,
- CPU profilers pokazują zużycie CPU, nie całkowity elapsed time,
- thread states są kluczowe do interpretacji problemów wydajnościowych,
- RUNNABLE zwykle oznacza aktywną pracę CPU,
- WAITING / TIMED_WAITING oznacza oczekiwanie,
- hotspot CPU i bottleneck I/O to zupełnie różne problemy,
- JFR i JMC pozwalają korelować CPU samples ze stanami wątków,
- poprawna diagnoza wymaga rozdzielenia CPU-bound od wait-bound behavior.