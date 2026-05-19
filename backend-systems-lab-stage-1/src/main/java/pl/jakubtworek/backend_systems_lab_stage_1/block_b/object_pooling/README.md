# Case 15 — Object pooling: kiedy szkodzi bardziej niż pomaga

## Wprowadzenie

Object pooling jest jednym z najbardziej klasycznych wzorców optymalizacyjnych w historii programowania.

Idea wydaje się intuicyjna:

> skoro tworzenie obiektów kosztuje, to lepiej je wielokrotnie wykorzystywać.

W starszych środowiskach runtime:
- często rzeczywiście miało to sens,
- GC był wolniejszy,
- alokacje były droższe,
- pamięć była bardziej ograniczona.

Problem polega na tym, że:
> nowoczesny JVM działa zupełnie inaczej niż wiele osób zakłada.

I właśnie dlatego:
- pooling zwykłych obiektów
  bardzo często:
    - nie pomaga,
    - komplikuje system,
    - pogarsza locality,
    - zwiększa pressure na old generation,
    - a czasem wręcz obniża throughput.

---

# Fundamentalne nieporozumienie

Największy błąd intuicyjny wygląda tak:

> „alokacja obiektu jest bardzo droga”

W nowoczesnym HotSpot:
- krótkotrwała alokacja bywa ekstremalnie tania.

Bardzo często:
- to praktycznie tylko:
    - przesunięcie wskaźnika w pamięci.

Czyli:
## pointer bump allocation

To fundamentalnie zmienia opłacalność pooling’u.

---

# Young generation jest zoptymalizowana pod churn

Nowoczesne collectory JVM zakładają:
- ogromną liczbę krótkotrwałych obiektów.

Dlatego:
- young generation
  jest zoptymalizowana właśnie pod:
    - szybkie tworzenie,
    - szybkie umieranie obiektów.

To bardzo ważna intuicja:
> JVM oczekuje allocation churn i potrafi obsługiwać go bardzo efektywnie.

---

# Krótkotrwałe obiekty są „tanie”

Jeżeli obiekt:
- powstaje,
- szybko umiera,
- nie przeżywa young GC,

to:
- jego koszt GC często jest niewielki.

W praktyce:
- taki obiekt może nigdy nie trafić do old generation,
- nigdy nie uczestniczyć w kosztownych full collections.

I właśnie dlatego:
> krótkie lifetime bywają bardziej korzystne niż reuse.

---

# Co robi pooling

Pooling zmienia:
## lifetime obiektów

Obiekt:
- zamiast szybko umrzeć,
- zostaje zachowany,
- wraca do puli,
- żyje znacznie dłużej.

To oznacza:
- większą szansę promotion do old generation,
- większy live set,
- większą presję na old-gen GC.

I tutaj pojawia się kluczowy paradoks:
> pooling może zmniejszyć allocation rate, ale zwiększyć koszt utrzymywania pamięci.

---

# Old generation jest znacznie droższa

Young GC:
- jest zwykle szybkie,
- zoptymalizowane pod krótkotrwałe obiekty.

Natomiast:
- old generation
  jest dużo bardziej kosztowna:
    - marking,
    - compaction,
    - relocation,
    - scanning live objects.

Im większy live set:
- tym trudniejsza praca collectora.

Pooling bardzo często:
- sztucznie powiększa live set.

---

# Pooling zmienia problem, nie usuwa go

To jedna z najważniejszych intuicji tego case’u.

Pooling:
- nie eliminuje kosztów,
- tylko przesuwa je w inne miejsce.

Zamiast:
- allocation churn,

pojawiają się:
- old-gen pressure,
- synchronization,
- reset logic,
- cache locality problems,
- stale object bugs,
- contention.

---

# Resetowanie obiektów też kosztuje

Pooled object:
- musi zostać wyczyszczony,
- zanim zostanie użyty ponownie.

Czyli:
- reset pól,
- czyszczenie tablic,
- usuwanie referencji,
- przywracanie stanu.

To:
- również kosztuje CPU,
- może generować dodatkowy memory traffic.

Czasem:
> reset obiektu jest droższy niż stworzenie nowego.

---

# Escape Analysis zmienia opłacalność pooling’u

Nowoczesny JVM używa:
## Escape Analysis

Jeżeli obiekt:
- nie opuszcza scope,
- nie jest współdzielony,
- jest lokalny dla metody,

to JIT może:
- wyeliminować alokację,
- zastąpić obiekt scalarized values.

To oznacza:
> pooling może uniemożliwić optymalizacje, które JVM wykonałby automatycznie.

---

# Pooling a synchronizacja

W systemach wielowątkowych:
- pula zwykle jest współdzielona.

To oznacza:
- locki,
- contention,
- CAS,
- kolejki,
- synchronization overhead.

I bardzo często:
- koszt synchronizacji
  przewyższa koszt normalnej alokacji.

---

# Pool jako bottleneck skalowania

Shared pool:
- staje się współdzielonym punktem contention.

Przy dużej liczbie wątków:
- acquire/release
  mogą stać się bottleneckiem.

Wtedy:
- pooling redukuje allocation,
- ale niszczy skalowanie concurrency.

---

# Locality pamięci

Nowo zaalokowane obiekty:
- często mają bardzo dobrą locality,
- znajdują się blisko siebie w young generation.

Pooling:
- utrzymuje stare obiekty,
- rozproszone po pamięci,
- często w old generation.

To pogarsza:
- cache locality,
- memory prefetching,
- CPU efficiency.

---

# Stale state bugs

Pooling zwiększa również:
## complexity correctness.

Błędy typu:
- niewyczyszczone pola,
- pozostawione referencje,
- reuse błędnego stanu,
- memory leaks

są bardzo częste w ręcznie zarządzanych pulach.

To bardzo ważny koszt:
- nie tylko wydajnościowy,
- ale również utrzymaniowy.

---

# Pooling ma sens — ale dla innych rzeczy

To bardzo ważne.

Wniosek nie brzmi:
> „pooling jest zawsze zły”

Pooling nadal ma ogromny sens dla:
- połączeń DB,
- socketów,
- thread pools,
- GPU resources,
- native buffers,
- kosztownych external resources.

Czyli:
- zasobów,
  których stworzenie jest naprawdę drogie.

---

# Problem z „mikrooptymalizacją pamięci”

Wiele poolingów powstaje dlatego, że:
- ktoś widzi dużo alokacji,
- i zakłada, że to problem.

Ale:
> wysoka liczba alokacji sama w sobie nie oznacza problemu.

Nowoczesny JVM:
- został zaprojektowany do obsługi ogromnego allocation rate.

Dlatego:
- należy mierzyć realne bottlenecki,
- a nie optymalizować „na intuicję”.

---

# Allocation rate vs live set

To jedna z najważniejszych intuicji GC.

Nie tylko:
- liczba alokacji
  ma znaczenie.

Równie ważne jest:
- jak długo obiekty żyją.

Pooling:
- zmniejsza allocation rate,
- ale zwiększa object lifetime.

I bardzo często:
- to pogarsza sytuację GC.

---

# TLAB — dlaczego alokacja jest tania

JVM używa:
## Thread Local Allocation Buffers

Każdy wątek:
- posiada lokalny fragment heapu,
- może alokować bez locków.

To oznacza:
- bardzo tanią alokację,
- minimalną synchronizację.

To kolejny powód, dla którego:
> ręczny pooling zwykłych obiektów często przegrywa z normalną alokacją.

---

# Dlaczego benchmarki pooling’u bywają mylące

Na małych benchmarkach:
- pooling czasem wygląda szybciej.

Ale:
- benchmark nie pokazuje:
    - old-gen pressure,
    - long-term GC behavior,
    - fragmentation,
    - contention,
    - locality degradation.

Dlatego:
> pooling należy analizować przez JFR i GC logs, a nie tylko przez throughput.

---

# Najczęstszy błąd architektoniczny

Najczęstszy błąd wygląda tak:

> „Widzimy dużo alokacji, zróbmy object pool”

bez analizy:
- czy allocation naprawdę jest problemem,
- jak wygląda GC,
- jak wygląda object lifetime,
- jaki będzie koszt synchronizacji.

To bardzo często prowadzi do:
- bardziej skomplikowanego,
- wolniejszego systemu.

---

# Najważniejsza intuicja praktyczna

Najbardziej praktyczny wniosek brzmi:

> nowoczesny JVM bardzo dobrze radzi sobie z krótkotrwałymi obiektami, a pooling zwykłych obiektów często walczy z optymalizacjami GC zamiast je wspierać.

I właśnie dlatego:
- pooling powinien być wyjątkiem,
- a nie domyślną strategią optymalizacji.

---

# Najważniejsze wnioski

Najbardziej użyteczny model mentalny wygląda tak:

- nowoczesny JVM bardzo tanio alokuje krótkotrwałe obiekty,
- young generation jest zoptymalizowana pod allocation churn,
- pooling wydłuża lifetime obiektów,
- dłuższe lifetime zwiększają pressure na old generation,
- pooling zmniejsza allocation rate, ale zwiększa live set,
- resetowanie pooled objects również kosztuje CPU,
- pooling może blokować Escape Analysis i scalar replacement,
- współdzielone pule mogą powodować contention,
- pooling pogarsza locality pamięci i cache behavior,
- pooling ma sens głównie dla naprawdę drogich zasobów zewnętrznych.