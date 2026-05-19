# Case 2 — Escape Analysis

## Wprowadzenie

Escape Analysis to jeden z najważniejszych mechanizmów optymalizacyjnych nowoczesnego HotSpot JVM. Jednocześnie jest to temat bardzo często upraszczany lub błędnie tłumaczony, zwłaszcza w materiałach dotyczących wydajności Javy.

Najpopularniejsze uproszczenie brzmi:

> „Escape Analysis powoduje alokację obiektów na stacku”

To stwierdzenie jest mylące.

W praktyce Escape Analysis jest znacznie szerszym mechanizmem analizy przepływu danych wykonywanym przez kompilator JIT (głównie C2), którego celem jest odpowiedź na pytanie:

> „Czy obiekt może zostać zaobserwowany poza aktualnym kontekstem wykonywania?”

Od odpowiedzi na to pytanie zależy, czy JVM może:
- usunąć alokację,
- rozbić obiekt na wartości skalarne,
- usunąć synchronizację,
- uprościć kod maszynowy.

Escape Analysis jest więc przede wszystkim mechanizmem umożliwiającym agresywne optymalizacje JIT.

---

# Problem kosztu alokacji

Logiczny model Javy sugeruje, że każde:

```java
new Object()
```

oznacza:
- alokację pamięci,
- utworzenie obiektu,
- późniejsze sprzątanie przez Garbage Collector.

Gdyby JVM rzeczywiście wykonywał wszystkie te operacje dosłownie dla każdego obiektu, koszt krótkotrwałych alokacji byłby ogromny.

W rzeczywistości nowoczesny HotSpot próbuje udowodnić, że część obiektów:
- nie jest istotna semantycznie,
- nie musi fizycznie istnieć,
- może zostać zoptymalizowana.

To właśnie rola Escape Analysis.

---

# Co oznacza „escape”

Obiekt „ucieka” (*escapes*), gdy może zostać zaobserwowany poza aktualnym kontekstem wykonywania.

Przykładowo:
- zapisanie obiektu do pola klasy,
- zwrócenie obiektu z metody,
- przekazanie do innego wątku,
- zapisanie do struktury współdzielonej,

powoduje, że JVM musi założyć:
- obiekt istnieje fizycznie,
- jego tożsamość może być obserwowana,
- optymalizacje są ograniczone.

Natomiast jeśli obiekt:
- istnieje wyłącznie lokalnie,
- jest używany tylko wewnątrz jednej metody,
- jego referencja nigdzie nie „wycieka”,

to JVM może potraktować go jako czysto tymczasową konstrukcję logiczną.

---

# Najważniejsze nieporozumienie — „stack allocation”

Wiele materiałów tłumaczy Escape Analysis jako:

> „obiekt zostaje przeniesiony na stack”

To uproszczenie jest niebezpieczne, ponieważ sugeruje fizyczny model pamięci, który nie oddaje działania HotSpot JVM.

W praktyce najczęściej dzieje się coś innego:

- alokacja zostaje całkowicie usunięta,
- obiekt przestaje istnieć jako byt runtime,
- pola obiektu stają się zwykłymi wartościami skalarnymi,
- JVM operuje bezpośrednio na rejestrach CPU.

To optymalizacja nazywana:

## Scalar Replacement

Przykład:

```java
Point p = new Point(1, 2);
return p.x + p.y;
```

Jeżeli obiekt `Point`:
- nie ucieka,
- nie jest obserwowana jego tożsamość,
- nie jest synchronizowany,

to JIT może dojść do wniosku:

> „sam obiekt nie jest potrzebny”

i zamienić kod logicznie na coś podobnego do:

```java
int x = 1;
int y = 2;
return x + y;
```

Bez alokacji.
Bez heap.
Bez GC.

---

# Escape Analysis a Garbage Collector

To bardzo ważna zależność.

Jeżeli alokacja zostaje usunięta:
- obiekt nigdy nie trafia na heap,
- Garbage Collector nigdy go nie widzi,
- nie istnieje koszt późniejszego sprzątania.

Dlatego Escape Analysis może znacząco zmniejszać:
- presję na GC,
- liczbę krótkotrwałych obiektów,
- throughput allocation rate.

W nowoczesnych aplikacjach wysokowydajnych jest to jeden z kluczowych mechanizmów redukujących overhead pamięciowy.

---

# Lock Elision — usuwanie synchronizacji

Escape Analysis nie służy wyłącznie do eliminacji alokacji.

Drugim bardzo ważnym zastosowaniem jest:

## Lock Elision

Jeżeli JVM potrafi udowodnić, że:
- obiekt blokady jest lokalny,
- żaden inny wątek nie może go zobaczyć,

to synchronizacja staje się semantycznie zbędna.

Przykład:

```java
Object lock = new Object();

synchronized (lock) {
    doWork();
}
```

Jeżeli `lock`:
- nie ucieka,
- istnieje wyłącznie lokalnie,

to JVM może całkowicie usunąć monitor enter / exit.

To ogromna optymalizacja, ponieważ synchronizacja — nawet zoptymalizowana — nadal posiada koszt.

---

# Dlaczego benchmarki JMH są potrzebne

Escape Analysis jest optymalizacją JIT.

To oznacza:
- zależy od profilu wykonania,
- zależy od inliningu,
- zależy od wersji JVM,
- zależy od C1/C2,
- może działać inaczej między uruchomieniami.

Zwykły benchmark typu:

```java
long start = System.nanoTime();
```

jest praktycznie bezużyteczny dla takich eksperymentów.

Potrzebny jest framework, który:
- rozgrzewa JVM,
- stabilizuje kompilację,
- kontroluje dead-code elimination,
- separuje fazy pomiaru.

Dlatego używa się:
## JMH (Java Microbenchmark Harness)

---

# Dlaczego „return object” jest trudniejsze

W przykładzie benchmarku:

```java
return new Point(x, y);
```

sytuacja robi się bardziej złożona.

Teoretycznie:
- obiekt opuszcza metodę,
- więc „ucieka”.

Ale:
- jeśli metoda zostanie zinline’owana,
- a caller używa obiektu lokalnie,

to Escape Analysis może nadal usunąć alokację.

To bardzo ważna obserwacja:

> Escape Analysis działa na zoptymalizowanym grafie programu po inliningu, a nie wyłącznie na pojedynczej metodzie źródłowej.

Dlatego przewidywanie zachowania JVM „na oko” jest często błędne.

---

# Czego Escape Analysis NIE gwarantuje

Escape Analysis:
- nie gwarantuje stack allocation,
- nie gwarantuje eliminacji każdej alokacji,
- nie działa identycznie między wersjami JVM,
- nie jest częścią specyfikacji Java Language Specification.

To wyłącznie optymalizacja implementacyjna HotSpot.

Kod musi być poprawny również wtedy, gdy EA nie zadziała.

---

# Najważniejsze wnioski

Najbardziej poprawny model mentalny wygląda tak:

- Escape Analysis jest optymalizacją JIT wykonywaną głównie przez C2,
- JVM analizuje, czy obiekt może zostać zaobserwowany poza lokalnym kontekstem,
- jeśli obiekt nie ucieka, część alokacji może zostać usunięta,
- najczęściej nie chodzi o „przeniesienie na stack”, lecz o eliminację obiektu,
- Scalar Replacement pozwala zastąpić obiekt zwykłymi wartościami,
- EA może usuwać również synchronizację,
- efektem jest mniejsza presja na GC i mniej overheadu runtime,
- zachowanie EA zależy od inliningu i optymalizacji JIT,
- benchmarki wydajnościowe wymagają JMH.