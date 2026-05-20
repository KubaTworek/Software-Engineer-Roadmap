# Case 4 — Stream vs for-loop

## Wprowadzenie

Porównanie:
- `Stream API`
- oraz klasycznej pętli `for`

jest jednym z najbardziej kontrowersyjnych tematów wydajnościowych w Javie.

Bardzo łatwo popaść tutaj w skrajności:
- „Streamy są wolne”
- albo:
- „JIT wszystko zoptymalizuje”

Oba stwierdzenia są zbyt uproszczone.

W praktyce różnice zwykle nie wynikają z samej idei Stream API, lecz z:
- dodatkowego narzutu abstrakcji,
- boxingu,
- dispatchu metod,
- tworzenia pipeline’ów,
- materializacji danych,
- wpływu na allocation rate,
- wpływu na CPU cache locality,
- trudności optymalizacyjnych dla JIT.

Najważniejsza intuicja brzmi:

> Stream nie jest „zły”, ale w hot path koszt abstrakcji zaczyna mieć znaczenie.

---

# Dlaczego klasyczny for-loop jest tak szybki

Klasyczna pętla:

```java
for (int i = 0; i < array.length; i++)
```

jest bardzo prostą konstrukcją dla JVM.

JIT widzi:
- liniowy dostęp do pamięci,
- przewidywalny przepływ sterowania,
- brak dodatkowych obiektów,
- minimalny dispatch,
- bardzo prostą semantykę.

To pozwala na:
- agresywny inlining,
- eliminację bounds checks,
- lepszą optymalizację rejestrów,
- lepszą locality danych,
- czasami vectorization.

W przypadku:
- prymitywnych tablic,
- prostych operacji matematycznych,
- tight loops,

for-loop często jest praktycznie idealnym przypadkiem dla CPU i JIT.

---

# Co dodaje Stream API

Stream API wprowadza dodatkową warstwę abstrakcji.

Kod:

```java
stream()
    .filter(...)
    .map(...)
    .sum()
```

jest bardzo czytelny semantycznie, ale runtime oznacza:
- pipeline operacji,
- chaining funkcji,
- lambdy,
- dodatkowy dispatch,
- iteratory / spliteratory,
- trudniejszą analizę dla JIT.

W nowoczesnych JVM część tego narzutu jest optymalizowana, ale nie wszystko znika.

Zwłaszcza w:
- małych operacjach,
- bardzo gorących ścieżkach,
- kodzie CPU-bound,

koszt abstrakcji może stać się zauważalny.

---

# Primitive streams vs boxed streams

To jedno z najważniejszych rozróżnień.

## IntStream

pracuje na:
- prymitywnych `int`

Natomiast:

```java
Stream<Integer>
```

pracuje na:
- obiektach `Integer`.

To oznacza:
- boxing,
- unboxing,
- dodatkowe referencje,
- gorszą locality pamięci,
- większy pressure na cache CPU,
- większą presję na GC.

W praktyce:

```java
IntStream
```

jest zwykle znacznie tańszy niż:

```java
Stream<Integer>
```

szczególnie w hot path.

---

# Boxing — ukryty koszt

Boxing jest często niedoceniany.

Każdy `Integer` to:
- osobny obiekt,
- header JVM,
- referencja,
- dodatkowy koszt dereferencji.

Dla CPU różnica między:
- `int[]`
- a `List<Integer>`

jest ogromna.

`int[]`:
- przechowuje dane liniowo,
- jest cache-friendly,
- minimalizuje pointer chasing.

Natomiast `List<Integer>`:
- przechowuje referencje,
- prowadzi do skakania po pamięci,
- pogarsza cache locality.

W nowoczesnych CPU locality pamięci bywa ważniejsza niż sama liczba instrukcji.

---

# Allocation overhead

Stream sam w sobie nie zawsze oznacza ogromne alokacje.

To ważne, bo wiele materiałów przesadza ten aspekt.

Natomiast pewne wzorce streamowe mogą generować:
- dodatkowe obiekty pipeline,
- obiekty lambda,
- result collections,
- tymczasowe struktury danych.

Największy problem pojawia się zwykle przy:

```java
stream()
    .filter(...)
    .map(...)
    .collect(...)
```

ponieważ:
- materializowany jest nowy rezultat,
- tworzone są nowe kolekcje,
- rośnie allocation rate.

W hot path może to oznaczać:
- większy allocation churn,
- większą presję na young generation,
- częstsze minor GC.

---

# Dispatch i branch prediction

For-loop daje CPU bardzo przewidywalny przepływ wykonania.

Stream pipeline często oznacza:
- więcej pośrednich wywołań,
- więcej warstw abstrakcji,
- bardziej rozproszony kod maszynowy.

To wpływa na:
- branch prediction,
- instruction cache,
- efektywność pipeline CPU.

Przy dużych wolumenach danych te efekty zaczynają być mierzalne.

---

# JIT i inlining

Nowoczesny HotSpot jest bardzo agresywny.

JIT potrafi:
- inline’ować lambdy,
- inline’ować część stream pipeline,
- eliminować część overheadu,
- upraszczać dispatch.

Ale istnieją ograniczenia:
- zbyt duży graph optymalizacyjny,
- megamorphic call sites,
- złożone pipeline,
- trudności z alias analysis.

Dlatego:
> „JIT wszystko zoptymalizuje”

nie jest bezpiecznym założeniem.

W praktyce:
- prosty for-loop nadal bywa łatwiejszy do zoptymalizowania.

---

# Parallel Stream — częsty overengineering

`parallelStream()` wygląda atrakcyjnie:

```java
list.parallelStream()
```

ale w praktyce:
- wprowadza koszt synchronizacji,
- używa ForkJoinPool,
- wymaga dzielenia workloadu,
- zwiększa coordination overhead,
- może pogarszać locality.

Parallel stream pomaga głównie wtedy, gdy:
- workload jest duży,
- operacje są CPU-heavy,
- koszt per element jest znaczący.

Dla:
- małych kolekcji,
- lekkich operacji,
- memory-bound workloadów,

parallel stream bardzo często jest wolniejszy.

---

# Dlaczego potrzebny jest JMH

Porównania:
- Stream vs for-loop

są ekstremalnie podatne na błędy benchmarkowe.

JVM:
- inline’uje kod,
- usuwa dead code,
- zmienia strategię kompilacji runtime,
- optymalizuje po warmupie.

Dlatego benchmark typu:

```java
long start = System.nanoTime();
```

jest praktycznie bezwartościowy.

Potrzebny jest:
## JMH

który:
- wykonuje warmup,
- kontroluje dead-code elimination,
- stabilizuje JIT,
- izoluje benchmarki,
- poprawnie mierzy throughput i latency.

---

# Najważniejsza praktyczna intuicja

Najważniejszy wniosek nie brzmi:

> „for-loop jest zawsze lepszy”

To byłoby błędne.

Poprawna intuicja brzmi:

- Stream API poprawia czytelność i composability,
- dla większości aplikacji overhead jest akceptowalny,
- w hot path narzut abstrakcji może stać się istotny,
- największe koszty zwykle wynikają z:
    - boxingu,
    - allocation rate,
    - cache locality,
    - dispatch overhead.

Dlatego decyzja powinna zależeć od:
- charakterystyki workloadu,
- profilu wydajnościowego,
- krytyczności ścieżki wykonania.

---

# Najważniejsze wnioski

Najbardziej użyteczny model mentalny wygląda tak:

- for-loop jest bardzo prostą i przewidywalną konstrukcją dla JVM i CPU,
- Stream API dodaje warstwę abstrakcji oraz dodatkowy runtime overhead,
- `IntStream` jest znacznie tańszy niż `Stream<Integer>`,
- boxing i unboxing często są większym problemem niż sam stream,
- locality pamięci ma ogromny wpływ na wydajność,
- materializacja kolekcji zwiększa allocation rate,
- parallel stream nie jest automatycznie szybszy,
- JIT potrafi zoptymalizować część pipeline streamowego, ale nie wszystko,
- benchmarki wymagają JMH,
- wybór między streamem a pętlą powinien wynikać z charakterystyki hot path.