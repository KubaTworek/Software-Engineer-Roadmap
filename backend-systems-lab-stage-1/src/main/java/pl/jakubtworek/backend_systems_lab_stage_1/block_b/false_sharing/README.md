# Case 9 — False sharing: walka o cache line

## Wprowadzenie

False sharing jest jednym z najbardziej podstępnych problemów wydajnościowych w systemach wielowątkowych.

Dlaczego podstępnych?

Ponieważ:
- logicznie wątki nie współdzielą danych,
- nie ma locków,
- nie ma monitor contention,
- nie ma race condition,
- kod wygląda „idealnie równolegle”.

A mimo to:
- throughput gwałtownie spada,
- CPU usage rośnie,
- skalowanie na rdzeniach jest fatalne.

Problem nie leży w JVM.
Problem leży głębiej:
## w mechanizmach cache coherence CPU.

---

# Co naprawdę optymalizuje nowoczesny CPU

Współczesne procesory:
- nie operują bezpośrednio na RAM,
- używają wielu poziomów cache:
    - L1,
    - L2,
    - L3.

Dostęp do cache:
- jest wielokrotnie szybszy niż dostęp do pamięci RAM.

Dlatego CPU bardzo agresywnie:
- buforuje dane,
- synchronizuje cache między rdzeniami,
- optymalizuje locality pamięci.

I właśnie tutaj pojawia się false sharing.

---

# Cache line — podstawowa jednostka synchronizacji

CPU nie synchronizuje pojedynczych zmiennych.

Synchronizacja odbywa się na poziomie:
## cache line

czyli niewielkiego bloku pamięci.

Najczęściej:
- 64 bajty.

To oznacza:
- wiele różnych pól obiektu
  może znajdować się w tej samej cache line.

I to jest źródło problemu.

---

# Na czym polega false sharing

Wyobraźmy sobie:

```java
volatile long counter1;
volatile long counter2;
```

Logicznie:
- dwa różne wątki aktualizują różne liczniki,
- nie ma współdzielenia danych.

Ale fizycznie:
- oba pola mogą trafić do tej samej cache line.

Wtedy:
- rdzeń A modyfikuje `counter1`,
- rdzeń B modyfikuje `counter2`.

I mimo że pola są niezależne logicznie:
- CPU musi stale synchronizować całą cache line.

---

# Cache invalidation

To kluczowy mechanizm.

Kiedy rdzeń CPU zapisuje dane:
- inne rdzenie muszą unieważnić swoją kopię cache line.

Nawet jeśli:
- interesuje je inne pole znajdujące się w tej samej linii.

To prowadzi do:
- ciągłego invalidation,
- bounce cache line między rdzeniami,
- ogromnego ruchu synchronizacyjnego.

I właśnie to jest:
## false sharing

---

# Dlaczego throughput spada

W przypadku false sharing:
- CPU nie wykonuje użytecznej pracy,
- czas jest tracony na synchronizację cache.

Rdzenie:
- stale unieważniają sobie dane,
- stale pobierają nowe wersje cache line,
- walczą o ownership pamięci.

Efekt:
- więcej rdzeni nie daje proporcjonalnego przyspieszenia,
- czasem wydajność wręcz spada.

---

# Dlaczego problem jest trudny do zauważenia

False sharing:
- nie wygląda jak klasyczny bottleneck.

Nie zobaczysz:
- lock contention,
- blocked threads,
- monitor waits,
- wysokiego GC.

Problem objawia się jako:
- „dziwnie słabe skalowanie CPU”.

I właśnie dlatego:
- wiele osób błędnie szuka problemu w JVM,
- podczas gdy bottleneck jest sprzętowy.

---

# `volatile` i false sharing

`volatile` bardzo często pogarsza problem.

Dlaczego?

Ponieważ:
- zapis volatile wymusza visibility między rdzeniami,
- zwiększa synchronizację cache,
- wzmacnia traffic memory coherence.

To nie oznacza:
> „volatile jest zły”

Ale:
- częste zapisy do volatile field
  pod wysoką konkurencją
  mogą być bardzo kosztowne.

---

# Dlaczego padding pomaga

Padding polega na:
- sztucznym dodaniu pustych pól,
- odsunięciu hot variables od siebie w pamięci.

Celem jest:
- umieszczenie każdej gorącej zmiennej
  w osobnej cache line.

Przykład:

```java
long p1, p2, p3...
volatile long value;
long p4, p5, p6...
```

To zwiększa footprint pamięci,
ale:
- redukuje cache invalidation,
- poprawia skalowanie wielordzeniowe.

---

# `@Contended`

HotSpot posiada specjalną adnotację:

```java
@Contended
```

która:
- dodaje padding automatycznie,
- izoluje pola od siebie.

To wygodniejsze niż ręczne dodawanie pustych pól.

Ale:
- wymaga odpowiednich flag JVM,
- zwiększa zużycie pamięci,
- nie jest rozwiązaniem uniwersalnym.

---

# Dlaczego `LongAdder` istnieje

`LongAdder` jest jednym z najbardziej znanych przykładów projektowania pod false sharing.

Zamiast:
- jednego współdzielonego licznika,

używa:
- wielu niezależnych komórek,
- rozproszonych po pamięci.

To redukuje:
- contention,
- cache line bouncing,
- pressure na memory coherence.

Dlatego:
- `LongAdder`
  skaluje się znacznie lepiej przy dużej liczbie update.

---

# MESI protocol

False sharing wynika bezpośrednio z:
## MESI protocol

czyli mechanizmu synchronizacji cache między rdzeniami CPU.

Rdzeń:
- musi mieć ownership cache line przed zapisem,
- inne rdzenie tracą ważność swoich kopii.

To oznacza:
- nawet niezależne logicznie pola
  mogą generować koszt synchronizacji.

To fundamentalny problem architektury CPU,
nie JVM.

---

# Dlaczego problem rośnie wraz z liczbą rdzeni

Na:
- 2 rdzeniach,
- małej liczbie wątków,

false sharing bywa ledwo zauważalny.

Ale przy:
- 16,
- 32,
- 64 rdzeniach,

koszt coherence traffic gwałtownie rośnie.

W systemach high-throughput:
- false sharing potrafi zabić skalowanie aplikacji.

---

# JMH jest tutaj konieczny

False sharing:
- jest bardzo subtelnym problemem,
- wymaga precyzyjnych benchmarków.

JVM:
- inline’uje kod,
- usuwa dead code,
- zmienia układ pamięci,
- optymalizuje runtime.

Dlatego:
- benchmark typu `System.nanoTime()`
  jest niewiarygodny.

Potrzebny jest:
## JMH

który:
- poprawnie izoluje benchmark,
- stabilizuje warmup,
- redukuje błędy pomiarowe.

---

# Dlaczego problem jest trudny w Javie

Java:
- ukrywa layout pamięci,
- nie daje pełnej kontroli nad strukturą obiektów,
- JVM może zmieniać układ pól.

To oznacza:
- false sharing jest mniej oczywisty niż w C/C++,
- ale nadal bardzo realny.

Szczególnie przy:
- counters,
- ring buffers,
- queues,
- metrics,
- actor systems,
- disruptor-like architectures.

---

# Najważniejsza intuicja praktyczna

Najbardziej praktyczny wniosek brzmi:

> współbieżność nie kończy się na lockach i atomikach — bardzo często prawdziwy bottleneck znajduje się w cache coherence CPU.

I właśnie dlatego:
- fizyczny układ danych w pamięci
  może mieć ogromny wpływ na throughput.

---

# Najważniejsze wnioski

Najbardziej użyteczny model mentalny wygląda tak:

- CPU synchronizuje dane na poziomie cache line, nie pojedynczych pól,
- logicznie niezależne zmienne mogą współdzielić tę samą cache line,
- false sharing powoduje kosztowny cache invalidation traffic,
- problem nie wymaga locków ani współdzielonych danych logicznie,
- throughput może gwałtownie spadać mimo braku klasycznego contention,
- `volatile` wzmacnia synchronizację cache między rdzeniami,
- padding i `@Contended` pomagają izolować gorące pola,
- `LongAdder` jest projektowany z myślą o ograniczaniu false sharing,
- problem rośnie wraz z liczbą rdzeni CPU,
- false sharing jest problemem architektury CPU i cache coherence, nie samej JVM.