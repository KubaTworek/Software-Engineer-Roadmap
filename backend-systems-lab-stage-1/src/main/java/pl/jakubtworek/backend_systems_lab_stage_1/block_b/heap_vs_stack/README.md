# Case 1 — Heap vs Stack

## Wprowadzenie

Jednym z najważniejszych fundamentów zrozumienia JVM jest poprawne rozróżnienie pomiędzy pamięcią stosu (*stack memory*) a pamięcią sterty (*heap memory*). Bez tego bardzo trudno budować poprawne intuicje dotyczące działania Garbage Collectora, Escape Analysis, optymalizacji JIT czy nawet zwykłego debugowania aplikacji.

W praktyce wielu programistów upraszcza ten temat do stwierdzenia:

> „prymitywy są na stacku, obiekty na heapie”

To uproszczenie jest częściowo prawdziwe, ale prowadzi do wielu błędnych wniosków. W JVM sytuacja jest bardziej subtelna.

Najważniejsza rzecz, którą trzeba zrozumieć, brzmi:

- stack przechowuje lokalne zmienne i referencje,
- heap przechowuje obiekty oraz dane obiektów,
- referencja nie jest obiektem.

To właśnie referencje powodują najwięcej nieporozumień.

---

# Runtime Data Areas — mental model JVM

JVM dzieli pamięć runtime na kilka obszarów. W kontekście tego eksperymentu interesują nas głównie dwa:

## Stack Memory

Każdy wątek posiada własny stos (*Java Stack*).  
Dla każdego wywołania metody tworzony jest osobny *stack frame*.

W stack frame znajdują się między innymi:

- parametry metody,
- lokalne zmienne,
- referencje do obiektów,
- częściowo dane pomocnicze wykonywania metody.

Stack jest:
- szybki,
- uporządkowany,
- automatycznie czyszczony po zakończeniu metody.

Nie istnieje Garbage Collector dla stacka — pamięć znika wraz z końcem frame’a.

---

## Heap Memory

Heap jest współdzielony przez wszystkie wątki JVM.

To tutaj trafiają:
- obiekty,
- tablice,
- dane instancji klas.

Heap:
- jest zarządzany przez Garbage Collector,
- ma znacznie bardziej złożony cykl życia,
- jest miejscem większości kosztów pamięciowych aplikacji.

Każde `new` zwykle oznacza alokację na heapie.

---

# Co naprawdę dzieje się w przykładzie

W eksperymencie:

```java
Person personRef = new Person("Alice", 30);
```

dzieją się dwie różne rzeczy.

## 1. Tworzony jest obiekt na heapie

Instrukcja `new Person(...)` tworzy obiekt typu `Person`.

Ten obiekt zawiera:
- pole `name`,
- pole `age`,
- metadata obiektu,
- header używany przez JVM i GC.

Całość trafia na heap.

---

## 2. Na stacku pojawia się referencja

Zmienna:

```java
personRef
```

nie zawiera obiektu.

Zawiera jedynie referencję do obiektu.

Referencja jest lokalną zmienną metody `main()`, więc znajduje się w stack frame tej metody.

To bardzo ważne rozróżnienie:

- obiekt → heap,
- referencja do obiektu → stack.

---

# Dlaczego metoda modyfikuje obiekt

W przykładzie:

```java
modifyPerson(personRef);
```

do metody przekazywana jest kopia referencji.

Nie jest przekazywany sam obiekt.

W efekcie:
- `personRef` w `main()`
- oraz `person` w `modifyPerson()`

wskazują na ten sam obiekt na heapie.

Dlatego:

```java
person.name = "Bob";
```

modyfikuje dokładnie ten sam obiekt.

To nie jest „pass by reference”.

Java zawsze używa:
- *pass by value*.

Tyle że:
- dla prymitywów kopiowana jest wartość,
- dla obiektów kopiowana jest wartość referencji.

To jedna z najczęściej źle rozumianych rzeczy w Javie.

---

# Dlaczego primitive się nie zmienia

W przypadku:

```java
modifyPrimitive(primitiveValue);
```

kopiowana jest sama wartość `42`.

Metoda pracuje na swojej własnej kopii:

```java
value = 100;
```

Nie ma żadnego związku z oryginalną zmienną w `main()`.

Dlatego:

```java
System.out.println(primitiveValue);
```

nadal wypisuje:

```text
42
```

---

# Istotna intuicja pod Garbage Collector

GC interesują wyłącznie obiekty na heapie.

Garbage Collector analizuje:
- które obiekty są nadal osiągalne,
- przez jakie referencje można do nich dotrzeć.

Stack jest tutaj kluczowy, ponieważ lokalne referencje w stack frame są jednym z tzw. *GC Roots*.

Dopóki istnieje aktywna referencja:
- obiekt jest osiągalny,
- GC nie może go usunąć.

Kiedy metoda kończy działanie:
- stack frame znika,
- referencje znikają,
- obiekt może stać się kandydatem do GC.

---

# Fundament pod Escape Analysis

To rozróżnienie jest również niezbędne do zrozumienia Escape Analysis.

JIT może wykryć, że:
- obiekt nigdy nie „ucieka” poza metodę,
- nikt nie potrzebuje go na heapie,
- można go zoptymalizować.

W skrajnych przypadkach JVM może:
- całkowicie usunąć alokację,
- rozbić obiekt na prymitywy,
- przechowywać dane wyłącznie w rejestrach CPU.

Dlatego stwierdzenie:

> „obiekty zawsze są na heapie”

nie jest już w pełni prawdziwe w nowoczesnym HotSpot JVM.

Logicznie model nadal jest poprawny, ale fizyczna implementacja może zostać zoptymalizowana przez JIT.

---

# Najważniejsze wnioski

Najbardziej użyteczny model mentalny wygląda tak:

- stack przechowuje kontekst wykonywania metod,
- heap przechowuje obiekty,
- referencje są tylko wskaźnikami do obiektów,
- Java używa pass-by-value również dla referencji,
- Garbage Collector operuje na obiektach heapowych,
- stack frame są jednym z głównych GC Roots,
- nowoczesny JIT może eliminować część alokacji.