# Case 12 — BigDecimal w hot loop: koszt obiektowy i arytmetyczny

## Wprowadzenie

`BigDecimal` jest jednym z najczęściej używanych typów w Javie wszędzie tam, gdzie:
- wymagana jest dokładność dziesiętna,
- nie można zaakceptować błędów floating-point,
- ważna jest poprawność finansowa lub biznesowa.

I słusznie.

Problem zaczyna się jednak wtedy, gdy:
> `BigDecimal` trafia do hot loop.

Wtedy koszt przestaje dotyczyć wyłącznie:
- dokładności obliczeń,
- a zaczyna obejmować:
    - allocation pressure,
    - koszt CPU,
    - locality pamięci,
    - pressure na GC,
    - koszt immutable arithmetic.

To właśnie ten case pokazuje:
- że „poprawny biznesowo typ”
  może być bardzo kosztowny wydajnościowo w ścieżkach krytycznych.

---

# Dlaczego `BigDecimal` jest ciężki

`BigDecimal` nie jest prymitywem.

To:
- pełnoprawny obiekt,
- złożona reprezentacja liczby dziesiętnej,
- własna arytmetyka,
- własna semantyka precyzji i skali.

Operacje takie jak:
- `add()`,
- `multiply()`,
- `divide()`,
- `setScale()`

nie są prostymi instrukcjami CPU.

To:
- operacje na obiektach,
- często tworzące nowe instancje,
- wykonujące dodatkową logikę matematyczną.

---

# Immutability — fundamentalny koszt

`BigDecimal` jest:
## immutable

To oznacza:
- każda operacja tworzy nowy obiekt.

Przykład:

```java
total = total.add(amount);
```

nie modyfikuje istniejącego obiektu.

Tworzony jest:
- nowy `BigDecimal`,
- nowa reprezentacja liczby,
- często nowe struktury pomocnicze.

W hot loop:
- tysiące lub miliony takich operacji
  oznaczają ogromny allocation churn.

---

# Allocation pressure

To jeden z najważniejszych aspektów tego case’u.

Jeżeli w pętli:
- każda iteracja tworzy nowe obiekty,
- a operacje są wykonywane bardzo często,

to aplikacja:
- zalewa young generation,
- generuje ogromny allocation rate,
- zwiększa częstotliwość minor GC.

I właśnie dlatego:
> problemem często nie jest sam GC, lecz tempo tworzenia obiektów.

---

# CPU cost — nie tylko alokacje

To bardzo ważne.

`BigDecimal` jest kosztowny nie tylko dlatego, że tworzy obiekty.

Kosztowna jest również sama:
## arytmetyka dziesiętna.

CPU natywnie:
- bardzo dobrze obsługuje:
    - `int`,
    - `long`,
    - `double`.

Natomiast `BigDecimal`:
- wykonuje złożone operacje programowe,
- zarządza skalą,
- obsługuje precision,
- wykonuje dodatkowe walidacje.

To oznacza:
- więcej instrukcji,
- więcej branchy,
- więcej pracy runtime.

---

# `setScale()` w hot loop

To klasyczny anty-pattern wydajnościowy.

Kod typu:

```java
value.setScale(2, RoundingMode.HALF_UP)
```

wewnątrz gorącej pętli:
- często generuje dodatkowe obiekty,
- wykonuje kosztowne operacje rounding,
- zwiększa allocation pressure.

W praktyce:
> rounding per iteration potrafi być znacznie droższy niż rounding na granicy systemu.

---

# Granica domeny vs hot path

To jedna z najważniejszych intuicji praktycznych.

`BigDecimal` bardzo dobrze sprawdza się:
- na granicy API,
- w warstwie domenowej,
- w reprezentacji biznesowej,
- przy serializacji,
- przy walidacji finansowej.

Ale:
- nie zawsze jest dobrym wyborem
  dla:
- intensywnych obliczeń wewnętrznych,
- tight loops,
- batch processing,
- high-throughput engines.

---

# Reprezentacja skalowana (`long`)

Bardzo częstą optymalizacją jest:
## scaled integer representation

Przykład:
- kwota przechowywana jako:
    - liczba centów,
    - `long`.

Czyli:

```text
12.34 PLN
```

staje się:

```text
1234
```

To daje ogromne korzyści:
- brak allocation,
- natywna arytmetyka CPU,
- lepsza locality,
- mniej pracy GC.

---

# Dlaczego `long` jest tak szybki

`long`:
- jest prymitywem,
- nie wymaga dereferencji,
- nie tworzy obiektów,
- działa bezpośrednio na rejestrach CPU.

To oznacza:
- minimalny koszt pamięciowy,
- minimalny koszt GC,
- bardzo dobrą przewidywalność runtime.

Dla hot loop:
- to ogromna różnica.

---

# Boundary conversion

Bardzo często najlepszym kompromisem jest:

- wewnętrznie:
    - używać `long`,
- na granicy API:
    - konwertować do `BigDecimal`.

Czyli:
- obliczenia są tanie,
- semantyka domenowa pozostaje poprawna,
- interfejs biznesowy nadal używa `BigDecimal`.

To jeden z najbardziej praktycznych wzorców projektowych dla high-throughput systems.

---

# Wrappery immutable też mogą kosztować

To ważna obserwacja.

Kod:

```java
Money.plus(...)
```

może wyglądać „lekko”.

Ale jeżeli:
- wrapper jest immutable,
- każda operacja tworzy nowy obiekt,

to:
- allocation pressure nadal istnieje.

Czyli:
> sam fakt użycia `long` wewnątrz wrappera nie eliminuje kosztu obiektowego.

---

# `new BigDecimal(double)` — klasyczny problem

To jeden z najbardziej znanych błędów.

`double`:
- używa reprezentacji binarnej,
- nie reprezentuje dokładnie większości liczb dziesiętnych.

Dlatego:

```java
new BigDecimal(0.1)
```

tworzy:
- bardzo długą reprezentację binarną,
- często nieintuicyjną wartość.

To nie tylko problem semantyczny.
To również:
- dodatkowy koszt arytmetyczny,
- większa złożoność obiektu.

---

# `BigDecimal.valueOf()` — lepszy wybór

Znacznie lepszym rozwiązaniem jest:

```java
BigDecimal.valueOf(...)
```

lub:
- reprezentacja scaled integer.

To:
- unika problemów binarnych,
- zmniejsza koszt konstrukcji,
- daje bardziej przewidywalne zachowanie.

---

# Dlaczego GC zaczyna dominować

W przypadku intensywnego użycia `BigDecimal`:
- GC często zaczyna zbierać ogromne ilości krótkotrwałych obiektów.

To prowadzi do:
- większego allocation churn,
- częstszych young GC,
- większego CPU overhead,
- niestabilności latency.

I właśnie dlatego:
- pozornie „niewinny” typ biznesowy
  może stać się bottleneckiem systemu.

---

# JMH jest tutaj konieczny

Benchmarki `BigDecimal`:
- są bardzo podatne na błędy.

JVM może:
- inline’ować kod,
- eliminować część obliczeń,
- zmieniać zachowanie runtime po warmupie.

Dlatego:
- benchmark typu `nanoTime()`
  jest niewystarczający.

Potrzebny jest:
## JMH

który:
- stabilizuje pomiary,
- wykonuje warmup,
- kontroluje JIT,
- ogranicza błędy metodologiczne.

---

# Dlaczego nie należy wyciągać złych wniosków

To bardzo ważne.

Wniosek nie brzmi:
> „BigDecimal jest zły”

To byłoby błędne.

`BigDecimal`:
- rozwiązuje realny problem precyzji,
- jest poprawnym narzędziem biznesowym.

Problem pojawia się dopiero wtedy, gdy:
- trafia do bardzo gorących ścieżek,
- używany jest miliony razy na sekundę,
- generuje niepotrzebny allocation churn.

---

# Najważniejsza intuicja praktyczna

Najbardziej praktyczny wniosek brzmi:

> koszt `BigDecimal` w hot loop wynika jednocześnie z kosztu obiektowego i kosztu arytmetyki dziesiętnej.

I właśnie dlatego:
- decyzja o reprezentacji danych
  ma ogromny wpływ na CPU i GC.

---

# Najważniejsze wnioski

Najbardziej użyteczny model mentalny wygląda tak:

- `BigDecimal` jest ciężkim obiektem z własną arytmetyką dziesiętną,
- każda operacja immutable tworzy nowe obiekty,
- hot loop z `BigDecimal` generuje allocation pressure,
- problem dotyczy zarówno GC, jak i kosztu CPU,
- `setScale()` w pętli bywa bardzo kosztowny,
- reprezentacja scaled integer (`long`) jest znacznie tańsza runtime,
- boundary conversion często daje najlepszy kompromis,
- immutable wrappery nadal mogą generować allocation churn,
- `new BigDecimal(double)` jest problematyczne semantycznie i wydajnościowo,
- JMH jest konieczny do wiarygodnego benchmarkowania kosztu `BigDecimal`.