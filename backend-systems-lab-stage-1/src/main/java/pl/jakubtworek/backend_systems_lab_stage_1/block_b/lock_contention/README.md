# Case 8 — Lock contention: `synchronized` vs `AtomicLong` vs `LongAdder`

## Wprowadzenie

Jednym z najważniejszych problemów skalowania aplikacji wielowątkowych jest:
## contention

czyli sytuacja, w której wiele wątków konkuruje o ten sam zasób.

W praktyce bardzo często:
- problemem nie jest sama liczba wątków,
- lecz to, że wszystkie próbują wykonywać operacje na tej samej współdzielonej strukturze.

Klasyczny przykład:
- wspólny licznik,
- metryka,
- statystyka requestów,
- monitoring throughput,
- rate limiting.

Na pierwszy rzut oka:

```java
counter++
```

wydaje się banalną operacją.

W rzeczywistości:
- pod wysoką konkurencją
  staje się bardzo kosztowna.

---

# Dlaczego współdzielony licznik jest trudny

Problem polega na tym, że:

```java
counter++
```

nie jest pojedynczą operacją CPU.

To logicznie:
- odczyt wartości,
- modyfikacja,
- zapis nowej wartości.

Jeżeli wiele wątków wykonuje to równocześnie:
- pojawia się race condition,
- potrzebna jest synchronizacja,
- albo mechanizmy atomowe.

I właśnie tutaj zaczynają się kompromisy wydajnościowe.

---

# `synchronized` — klasyczny monitor JVM

Najbardziej klasyczne rozwiązanie:

```java
synchronized
```

używa:
## monitorów JVM

Monitor zapewnia:
- mutual exclusion,
- visibility,
- ordering memory operations.

Semantycznie jest bardzo mocny i bezpieczny.

Ale:
> tylko jeden wątek może wejść do sekcji krytycznej jednocześnie.

Przy małej liczbie wątków:
- koszt bywa akceptowalny.

Przy wysokiej kontencji:
- wątki zaczynają się blokować,
- pojawia się waiting,
- scheduler OS wykonuje więcej przełączeń kontekstu,
- throughput spada.

---

# Monitor contention

Jeżeli wiele wątków konkuruje o ten sam monitor:
- część z nich zostaje zablokowana,
- JVM musi nimi zarządzać,
- pojawia się parking/unparking,
- rośnie koszt synchronizacji.

W JFR widać to jako:
- `Java Monitor Blocked`,
- `Thread Park`,
- wzrost czasu oczekiwania.

Wtedy CPU często nie wykonuje użytecznej pracy aplikacyjnej.

Czas jest tracony na:
- kontencję,
- zarządzanie monitorami,
- scheduling.

---

# `AtomicLong` — lock-free, ale nie contention-free

`AtomicLong` wygląda atrakcyjnie, ponieważ:
- nie używa monitorów,
- nie blokuje wątków,
- bazuje na operacjach atomowych CPU.

Najważniejszym mechanizmem jest:
## CAS — Compare-And-Swap

CPU wykonuje:
- porównanie wartości,
- próbę zapisu nowej wartości,
- atomowo.

Jeżeli inny wątek zmienił wartość wcześniej:
- operacja się nie powiedzie,
- trzeba wykonać retry.

To oznacza:
> `AtomicLong` eliminuje blocking, ale nie eliminuje contention.

---

# Problem CAS contention

Przy dużej liczbie rdzeni:
- wszystkie wątki nadal walczą o tę samą lokalizację pamięci.

To prowadzi do:
- failed CAS retries,
- cache invalidation,
- memory synchronization traffic,
- CPU spinning.

W praktyce:
- wątki nie są blokowane,
- ale CPU może być intensywnie zużywane na retry.

Dlatego:
> lock-free nie oznacza „darmowe”.

---

# Cache coherence i shared memory

To bardzo ważny aspekt.

Współdzielona zmienna:
- musi być synchronizowana między cache CPU,
- powoduje invalidation cache lines,
- generuje ruch między rdzeniami.

Im więcej rdzeni:
- tym koszt synchronizacji cache rośnie.

To właśnie dlatego:
- bardzo prosty licznik
  potrafi stać się bottleneckiem skalowania.

---

# `LongAdder` — inna filozofia

`LongAdder` został zaprojektowany specjalnie dla:
- wysokiej częstotliwości aktualizacji,
- dużej liczby wątków,
- scenariuszy typu metrics/counters.

Kluczowa idea:
> nie wszystkie wątki aktualizują tę samą zmienną.

Zamiast tego:
- licznik jest rozbijany na wiele komórek,
- różne wątki aktualizują różne segmenty,
- contention zostaje rozproszony.

To podejście nazywa się często:
## striped counters

---

# Dlaczego `LongAdder` skaluje się lepiej

Zamiast:

```text
wszyscy walczą o jedną wartość
```

mamy:

```text
wątki aktualizują różne komórki
```

To redukuje:
- contention,
- CAS retries,
- cache invalidation,
- pressure na shared memory.

W efekcie:
- throughput rośnie,
- skalowanie na rdzeniach jest znacznie lepsze.

---

# Dlaczego `LongAdder` nie jest zawsze lepszy

To bardzo ważne.

`LongAdder` optymalizuje:
- częste update,
- rzadkie odczyty.

Ale odczyt:

```java
sum()
```

musi:
- przejść przez wszystkie komórki,
- zsumować wartości.

To oznacza:
- odczyt jest droższy niż `AtomicLong.get()`.

Dlatego:
> `LongAdder` nie jest idealny dla workloadów z bardzo częstymi odczytami.

---

# `LongAdder` a consistency

`AtomicLong` daje:
- pojedynczą spójną wartość atomową.

`LongAdder`:
- optymalizuje throughput,
- ale chwilowo wynik może nie reprezentować „jednej atomowej chwili czasu”.

Dla:
- metrics,
- telemetry,
- counters,
- monitoring,

to zwykle jest akceptowalne.

Dla:
- finansów,
- ścisłych liczników transakcyjnych,
- synchronizacji logicznej,

może być niewystarczające.

---

# Dlaczego problem rośnie wraz z liczbą rdzeni

Na małej liczbie wątków:
- różnice bywają minimalne.

Ale wraz ze wzrostem:
- liczby rdzeni,
- liczby worker threads,
- intensywności update,

contention eksploduje.

I właśnie wtedy:
- `synchronized`
  zaczyna gwałtownie tracić throughput,
- `AtomicLong`
  zaczyna marnować CPU na retry,
- `LongAdder`
  zwykle skaluje się najlepiej.

---

# JFR i contention profiling

Ten case najlepiej analizować przez:
## JFR

Dlaczego?

Ponieważ problem nie zawsze objawia się:
- wysokim czasem GC,
- wysokim heap usage,
- wysoką liczbą alokacji.

Problemem może być po prostu:
- marnowanie CPU na synchronizację.

W JFR warto obserwować:
- `Java Monitor Blocked`,
- `Thread Park`,
- `CPU Load`,
- `Hot Methods`,
- `Method Profiling`.

---

# CAS też może być bottleneckiem

To bardzo częste nieporozumienie.

Wielu programistów zakłada:

> „lock-free = brak contention”

To błędne.

CAS:
- nadal synchronizuje współdzieloną pamięć,
- nadal wymaga koherencji cache,
- nadal może generować ogromny koszt pod dużą konkurencją.

Dlatego:
- `AtomicLong`
  przy bardzo dużym contention
  często przestaje skalować się liniowo.

---

# False sharing

W zaawansowanych scenariuszach pojawia się również:
## false sharing

czyli sytuacja, w której:
- różne zmienne logicznie są niezależne,
- ale trafiają do tej samej cache line CPU,
- rdzenie nadal się wzajemnie invalidują.

`LongAdder` jest projektowany również z myślą o ograniczaniu takich efektów.

---

# Najważniejsza intuicja praktyczna

Najbardziej praktyczny wniosek brzmi:

> problemem skalowania współbieżnego bardzo często nie jest sama liczba wątków, lecz contention wokół współdzielonego stanu.

I właśnie dlatego:
- sposób reprezentacji współdzielonych danych
  ma ogromny wpływ na throughput systemu.

---

# Najważniejsze wnioski

Najbardziej użyteczny model mentalny wygląda tak:

- `synchronized` używa monitorów JVM i może blokować wątki,
- `AtomicLong` eliminuje blocking, ale nadal cierpi na contention,
- CAS retries mogą zużywać ogromne ilości CPU,
- współdzielona pamięć powoduje cache coherence overhead,
- `LongAdder` rozprasza contention między wiele komórek,
- `LongAdder` zwykle lepiej skaluje się przy częstych update,
- odczyt `LongAdder` jest droższy niż `AtomicLong.get()`,
- `LongAdder` nie jest idealny dla wszystkich scenariuszy consistency,
- contention rośnie wraz z liczbą rdzeni,
- JFR bardzo dobrze pokazuje bottlenecki synchronizacji i contention.