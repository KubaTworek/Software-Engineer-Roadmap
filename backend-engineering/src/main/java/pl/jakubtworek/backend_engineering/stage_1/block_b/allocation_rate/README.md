# Case 3 — Allocation Rate / String Concatenation

## Wprowadzenie

Jednym z najczęstszych błędów w analizie wydajności JVM jest myślenie o Garbage Collectorze jako o głównym źródle problemu.

W praktyce bardzo często problemem nie jest sam GC, lecz:
- tempo alokacji,
- liczba tworzonych obiektów,
- ilość krótkotrwałego garbage,
- ciągła presja na young generation.

To rozróżnienie jest krytyczne.

GC zwykle nie „psuje” wydajności sam z siebie. GC najczęściej reaguje na to, że aplikacja produkuje ogromne ilości tymczasowych obiektów.

String concatenation jest jednym z najlepszych przykładów pokazujących ten mechanizm.

---

# Allocation Rate — najważniejsza metryka

W nowoczesnych aplikacjach JVM bardzo często ważniejsza od:
- heap size,
- liczby GC pauses,
- total GC time,

jest metryka:

## allocation rate

czyli:

> ile pamięci aplikacja alokuje na sekundę.

Można mieć:
- bardzo krótki GC,
- bardzo nowoczesny collector,
- duży heap,

a mimo to mieć problem wydajnościowy, ponieważ aplikacja generuje ogromny churn obiektów.

GC wtedy nie jest przyczyną problemu.

GC jest skutkiem nadmiernej alokacji.

---

# Dlaczego String jest kosztowny

Kluczowa rzecz:

## String w Javie jest immutable

To oznacza:
- po utworzeniu obiektu nie można zmienić jego zawartości,
- każda „modyfikacja” tworzy nowy obiekt.

Przykład:

```java
result = result + i;
```

wygląda niewinnie, ale logicznie oznacza:
- stworzenie nowego bufora,
- skopiowanie poprzednich znaków,
- dopisanie nowych danych,
- stworzenie nowego Stringa,
- porzucenie poprzedniego obiektu.

Dzieje się to przy każdej iteracji pętli.

---

# Dlaczego concatenation generuje allocation churn

W pętli:

```java
for (int i = 0; i < N; i++) {
    result = result + i;
}
```

powstaje ogromna liczba:
- tymczasowych Stringów,
- tymczasowych bufferów,
- wewnętrznych tablic znaków / byte arrays.

Większość tych obiektów:
- żyje bardzo krótko,
- szybko staje się garbage,
- trafia do young generation.

To klasyczny przykład:

## allocation churn

czyli sytuacji, w której aplikacja:
- nie potrzebuje dużo trwałej pamięci,
- ale nieustannie produkuje ogromne ilości krótkotrwałych obiektów.

---

# Problem nie jest tylko w GC pause

To bardzo ważne.

Nawet jeśli GC radzi sobie szybko, wysoki allocation rate nadal powoduje koszt:
- ciągłych alokacji,
- kopiowania danych,
- pracy allocatorów,
- pracy barrierów GC,
- częstych minor collections,
- większej aktywności CPU.

Dlatego aplikacja może mieć:
- niskie pause times,
- brak Full GC,
- poprawny heap usage,

a mimo to działać znacznie wolniej niż powinna.

---

# Jak działa StringBuilder

`StringBuilder` rozwiązuje problem inaczej.

Zamiast:
- tworzyć nowy String przy każdej operacji,

utrzymuje:
- jeden mutowalny bufor,
- który jest rozszerzany w razie potrzeby.

To radykalnie zmniejsza liczbę:
- tymczasowych obiektów,
- kopiowań,
- alokacji.

W praktyce:
- allocation rate spada,
- young GC występuje rzadziej,
- pressure na allocator maleje.

---

# Dlaczego pre-sizing ma znaczenie

Nawet `StringBuilder` może generować niepotrzebne koszty.

Jeżeli builder zaczyna od małego bufora:
- JVM musi go wielokrotnie rozszerzać,
- tworzone są nowe większe tablice,
- stare dane są kopiowane.

To nadal jest koszt alokacji i kopiowania.

Dlatego:

```java
new StringBuilder(capacity)
```

pozwala ograniczyć:
- reallocations,
- buffer copying,
- dodatkowy garbage.

To szczególnie istotne w:
- parserach,
- serializerach,
- generatorach payloadów,
- systemach high-throughput.

---

# Co pokaże JFR

W tym case Java Flight Recorder bardzo dobrze pokazuje:
- liczbę alokowanych obiektów,
- allocation rate,
- hotspoty alokacyjne,
- częstotliwość GC.

Najciekawsze obserwacje zwykle pojawiają się w:
- `Memory → Allocation`,
- `Object Statistics`,
- `Garbage Collections`,
- `Allocation Flame Graph`.

Najczęściej widać:
- ogromną liczbę `String`,
- `byte[]`,
- `char[]`,
- obiektów pomocniczych związanych z konkatenacją.

---

# Istotna zmiana od Java 9+

W starszych wersjach Javy String używał:
- `char[]`

Od Java 9 wprowadzono:
## Compact Strings

W wielu przypadkach String przechowuje dane jako:
- `byte[]`

co zmniejsza footprint pamięciowy dla tekstu ASCII / Latin-1.

To jednak:
- nie eliminuje problemu allocation churn,
- nie zmienia immutability Stringa,
- nie usuwa kosztu kopiowania danych.

Dlatego nawet nowoczesne wersje JVM nadal cierpią przy intensywnej konkatenacji.

---

# Dlaczego compiler nie zawsze „naprawi” problem

To kolejny częsty mit.

Compiler rzeczywiście potrafi zoptymalizować:

```java
"a" + "b" + "c"
```

do pojedynczego literału.

JIT potrafi również optymalizować proste konkatenacje.

Ale w dynamicznej pętli:

```java
result = result + i;
```

problem nadal istnieje, ponieważ:
- zawartość zmienia się runtime,
- trzeba tworzyć nowe reprezentacje danych,
- kopiowanie staje się dominującym kosztem.

---

# Allocation pressure a skalowanie systemu

To jest bardzo praktyczny problem produkcyjny.

Aplikacja z wysokim allocation rate:
- zużywa więcej CPU,
- częściej aktywuje GC,
- gorzej skaluje throughput,
- produkuje większe jittery latency,
- gorzej działa pod loadem.

W systemach high-performance:
- redukcja allocation rate często daje większy efekt niż tuning samego GC.

To bardzo ważna intuicja:
> Najlepszy garbage collector to garbage, którego nigdy nie utworzono.

---

# Najważniejsze wnioski

Najbardziej użyteczny model mentalny wygląda tak:

- problemem wydajnościowym często nie jest sam GC, lecz tempo alokacji,
- String jest immutable, więc konkatenacja tworzy nowe obiekty,
- intensywna konkatenacja generuje allocation churn,
- young generation zostaje zalewana krótkotrwałym garbage,
- nawet szybki GC nadal wykonuje dodatkową pracę,
- StringBuilder redukuje liczbę alokacji dzięki mutowalnemu buforowi,
- pre-sizing ogranicza koszt rozszerzania bufora,
- allocation rate jest jedną z kluczowych metryk JVM,
- JFR bardzo dobrze pokazuje źródła allocation pressure,
- ograniczanie liczby alokacji często daje większy efekt niż tuning GC.