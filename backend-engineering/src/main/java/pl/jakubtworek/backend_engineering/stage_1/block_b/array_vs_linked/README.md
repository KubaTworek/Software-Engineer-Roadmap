# Case 13 — ArrayList vs LinkedList: cache locality wygrywa

## Wprowadzenie

Jednym z najbardziej klasycznych mitów związanych z wydajnością struktur danych w Javie jest przekonanie:

> „LinkedList jest szybszy do insertów, bo ArrayList musi kopiować elementy.”

Teoretycznie brzmi to logicznie.

Złożoność asymptotyczna mówi przecież:
- `ArrayList`:
    - insert w środku → `O(n)`
- `LinkedList`:
    - insert po znalezieniu noda → `O(1)`

A mimo to:
> w praktycznych benchmarkach `ArrayList` bardzo często wygrywa niemal we wszystkim.

Dlaczego?

Ponieważ nowoczesna wydajność systemów bardzo często zależy bardziej od:
- lokalności pamięci,
- cache CPU,
- kosztu dereferencji,
- przewidywalności pamięci,
- branch prediction,

niż od samej złożoności asymptotycznej.

I właśnie to pokazuje ten case.

---

# Problem nie dotyczy „API kolekcji”

To bardzo ważne.

Ten case:
- nie jest głównie o Javowych kolekcjach,
- nie jest o metodach `add()` i `get()`.

To przede wszystkim:
## case o memory subsystem CPU.

Czyli o:
- cache locality,
- pointer chasing,
- cache misses,
- układzie danych w pamięci,
- kosztach dereferencji.

---

# ArrayList — pamięć ciągła

`ArrayList` przechowuje dane w:
## ciągłej tablicy referencji.

To oznacza:
- elementy znajdują się blisko siebie w pamięci,
- CPU może skutecznie prefetchować dane,
- cache line zawiera wiele kolejnych elementów.

W efekcie:
- iteracja jest bardzo szybka,
- locality pamięci jest bardzo dobra,
- branch prediction działa efektywnie.

To ogromna przewaga nowoczesnych struktur tablicowych.

---

# LinkedList — rozproszone nody

`LinkedList` działa zupełnie inaczej.

Każdy element:
- jest osobnym node object,
- posiada referencję do:
    - previous,
    - next,
    - value.

To oznacza:
- wiele małych obiektów,
- rozproszenie po heapie,
- brak locality pamięci,
- dużo dereferencji.

I właśnie tutaj zaczyna się problem.

---

# Pointer chasing

Iteracja po `LinkedList` wygląda logicznie jak:

```text
node -> next -> next -> next
```

Każdy krok:
- wymaga odczytu kolejnego noda,
- często powoduje cache miss,
- wymaga pobrania danych z dalszych poziomów pamięci.

To zjawisko nazywa się:
## pointer chasing

I jest bardzo kosztowne dla CPU.

---

# Cache locality — prawdziwy bohater tego case’u

Nowoczesne CPU są projektowane pod:
- przewidywalny dostęp do pamięci,
- sekwencyjne odczyty,
- locality danych.

`ArrayList` idealnie pasuje do tego modelu:
- kolejne elementy leżą obok siebie,
- cache line jest efektywnie wykorzystywana.

`LinkedList`:
- praktycznie niszczy locality pamięci,
- wymusza skakanie po heapie.

I właśnie dlatego:
> ArrayList bardzo często wygrywa mimo gorszej teoretycznej złożoności.

---

# Cache miss — prawdziwy koszt

Największym kosztem nowoczesnych systemów często nie są:
- instrukcje CPU,
- operacje arytmetyczne.

Najdroższy jest:
## dostęp do pamięci.

Cache miss:
- może kosztować setki cykli CPU,
- zatrzymuje pipeline procesora,
- powoduje oczekiwanie na dane.

`LinkedList` generuje:
- znacznie więcej cache misses,
- znacznie gorsze locality behavior.

---

# Dlaczego iteracja jest tak ważna

W praktyce większość workloadów:
- dużo częściej iteruje,
- niż wykonuje insert w środku kolekcji.

I właśnie tutaj:
- `ArrayList`
  niemal zawsze wygrywa.

Ponieważ:
- iteracja po ciągłej tablicy
  jest ekstremalnie przyjazna dla cache CPU.

---

# Złożoność asymptotyczna vs realny hardware

To bardzo ważna intuicja.

Big-O:
- ignoruje cache,
- ignoruje locality,
- ignoruje branch prediction,
- ignoruje memory hierarchy.

A współczesny hardware:
- jest silnie zależny od tych czynników.

Dlatego:
> struktura teoretycznie „gorsza” może być znacznie szybsza w praktyce.

---

# Dlaczego `LinkedList.get(i)` jest katastrofalne

`ArrayList.get(i)`:
- to bezpośredni dostęp do tablicy,
- praktycznie `O(1)` runtime.

Natomiast:
- `LinkedList.get(i)`
  musi:
- przejść przez kolejne nody.

To oznacza:
- traversal,
- dereferencje,
- cache misses,
- pointer chasing.

W praktyce:
- indexed access na `LinkedList`
  jest bardzo kosztowny.

---

# Allocation pressure

`LinkedList` ma jeszcze jeden problem:
## ogromną liczbę małych obiektów.

Każdy node:
- jest osobnym obiektem,
- posiada object header,
- posiada referencje.

To oznacza:
- większy memory footprint,
- większy allocation rate,
- większy pressure na GC.

`ArrayList`:
- posiada jedną dużą tablicę,
- jest dużo bardziej kompaktowy pamięciowo.

---

# Object overhead

Node `LinkedList`:
- to nie tylko wartość.

To również:
- object header,
- alignment,
- next reference,
- previous reference.

W efekcie:
- koszt pamięci jednego elementu
  jest wielokrotnie większy niż w `ArrayList`.

To pogarsza:
- locality,
- cache efficiency,
- GC behavior.

---

# Branch prediction

`ArrayList`:
- ma bardzo przewidywalny wzorzec dostępu do pamięci.

`LinkedList`:
- wymaga dynamicznego traversal,
- dereferencji pointerów,
- mniej przewidywalnych odczytów.

To utrudnia:
- branch prediction,
- prefetching CPU,
- pipeline efficiency.

---

# Dlaczego insert w środku nie zawsze ratuje LinkedList

Teoretycznie:
- insertion po znalezieniu noda jest `O(1)`.

Ale:
> znalezienie noda nadal kosztuje.

I właśnie traversal:
- często dominuje koszt całej operacji.

W praktyce:
- koszt pointer chasing
  bywa większy niż kopiowanie ciągłego fragmentu pamięci w `ArrayList`.

---

# CPU kocha pamięć ciągłą

To jedna z najważniejszych intuicji performance engineering.

Nowoczesne CPU są ekstremalnie zoptymalizowane pod:
- ciągły dostęp do pamięci,
- sekwencyjne dane,
- locality.

Dlatego:
- tablice,
- contiguous buffers,
- columnar storage,
- flat data structures

bardzo często wygrywają z:
- strukturami pointer-based.

---

# Primitive arrays — jeszcze lepsza locality

`ArrayList<Integer>` nadal:
- przechowuje referencje do boxed Integer.

Jeszcze lepszą locality daje:
## primitive array

czyli:

```java
int[]
```

Wtedy:
- dane są przechowywane bezpośrednio,
- bez boxing,
- bez dereferencji obiektów.

To zwykle:
- najlepszy wariant dla cache CPU.

---

# Dlaczego JMH jest tutaj konieczny

Ten case:
- jest bardzo zależny od hardware,
- cache behavior,
- JIT,
- branch prediction,
- warmupu.

Dlatego:
- benchmark typu `nanoTime()`
  jest niewiarygodny.

Potrzebny jest:
## JMH

który:
- poprawnie rozgrzewa JVM,
- stabilizuje pomiary,
- ogranicza błędy metodologiczne.

---

# Najczęstszy błąd interpretacyjny

Najczęstszy błąd wygląda tak:

> „LinkedList ma lepszą złożoność asymptotyczną, więc będzie szybszy”

To często błędne w praktyce.

Współczesna wydajność:
- jest bardzo mocno zależna od pamięci i cache CPU,
- a nie tylko od Big-O.

---

# Najważniejsza intuicja praktyczna

Najbardziej praktyczny wniosek brzmi:

> w nowoczesnych systemach locality pamięci bardzo często jest ważniejsza niż teoretyczna złożoność operacji.

I właśnie dlatego:
- struktury contiguous memory
  tak często wygrywają wydajnościowo.

---

# Najważniejsze wnioski

Najbardziej użyteczny model mentalny wygląda tak:

- `ArrayList` przechowuje dane w pamięci ciągłej,
- `LinkedList` używa rozproszonych node objects,
- locality pamięci ma ogromny wpływ na wydajność CPU,
- cache misses są bardzo kosztowne,
- pointer chasing pogarsza throughput,
- `LinkedList.get(i)` wymaga traversal przez nody,
- `LinkedList` generuje większy memory footprint i allocation pressure,
- nowoczesne CPU są zoptymalizowane pod sekwencyjny dostęp do pamięci,
- struktury contiguous memory często wygrywają mimo gorszego Big-O,
- primitive arrays oferują jeszcze lepszą locality niż `ArrayList<Integer>`.