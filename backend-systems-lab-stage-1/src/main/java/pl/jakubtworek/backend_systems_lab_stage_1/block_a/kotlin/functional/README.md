# Functional Programming — Java vs Kotlin

## Wprowadzenie

Ten moduł skupia się na różnicach pomiędzy Javą i Kotlinem w kontekście programowania funkcyjnego. Nie chodzi tutaj o pełne omówienie teorii programowania funkcyjnego, ale o pokazanie, jak oba języki wykorzystują elementy tego paradygmatu w codziennej pracy programisty.

Współczesne aplikacje backendowe bardzo często wykorzystują operacje takie jak:

* filtrowanie danych,
* transformacje kolekcji,
* mapowanie wyników,
* grupowanie,
* przetwarzanie strumieniowe,
* operacje na immutable collections,
* kompozycję funkcji.

Zarówno Java, jak i Kotlin wspierają taki styl programowania, ale robią to w inny sposób.

Java dodała większość mechanizmów funkcyjnych dopiero w wersji 8 wraz z:

* lambdami,
* Stream API,
* method references,
* functional interfaces.

Kotlin był projektowany od początku z dużo silniejszym wpływem programowania funkcyjnego. W efekcie wiele mechanizmów funkcyjnych jest bardziej naturalną częścią języka.

---

# Programowanie funkcyjne w praktyce

W praktycznych projektach programowanie funkcyjne bardzo rzadko oznacza budowanie całej aplikacji w czysto funkcyjnym stylu.

Znacznie częściej chodzi o wykorzystanie wybranych mechanizmów, które:

* upraszczają przetwarzanie danych,
* redukują ilość kodu imperatywnego,
* poprawiają czytelność transformacji,
* ograniczają mutowalność,
* zmniejszają liczbę efektów ubocznych.

Najbardziej typowym przykładem są operacje wykonywane na kolekcjach.

---

# Kolekcje i transformacje danych

Jednym z podstawowych zastosowań stylu funkcyjnego jest przetwarzanie kolekcji.

Przykładowo aplikacja może:

* filtrować użytkowników,
* mapować obiekty domenowe do DTO,
* grupować dane,
* agregować wyniki,
* wyszukiwać elementy spełniające warunki.

W klasycznym stylu imperatywnym często oznaczało to:

* ręczne pętle,
* mutable collections,
* tymczasowe zmienne,
* dużą ilość kodu technicznego.

Styl funkcyjny pozwala wyrazić te operacje bardziej deklaratywnie.

Zamiast mówić „jak” iterować po danych, kod opisuje bardziej „co” ma zostać wykonane.

---

# Stream API w Javie

Java wykorzystuje przede wszystkim Stream API.

Typowy przepływ wygląda następująco:

```java
users.stream()
    .filter(...)
    .map(...)
    .toList();
```

Stream tworzy pipeline operacji wykonywanych na danych.

Najczęściej używane operacje to:

* `filter`,
* `map`,
* `flatMap`,
* `reduce`,
* `collect`,
* `groupingBy`,
* `sorted`,
* `distinct`,
* `anyMatch`,
* `allMatch`.

Stream API było bardzo dużą zmianą w Javie, ponieważ pozwoliło odejść od dużej liczby manualnych pętli i mutable collections.

Jednocześnie Stream API jest dość rozbudowane i techniczne. W bardziej złożonych przypadkach kod może stać się trudniejszy do czytania, szczególnie przy użyciu skomplikowanych collectorów.

---

# Functional Collections w Kotlinie

Kotlin oferuje bardzo podobne operacje, ale są one dostępne bezpośrednio na kolekcjach.

Przykład:

```kotlin
users
    .filter { it.age >= 18 }
    .map { it.fullName }
```

Nie ma potrzeby wywoływania `.stream()`.

To jest jedna z najbardziej odczuwalnych różnic ergonomicznych pomiędzy Javą i Kotlinem.

Kotlin traktuje operacje funkcyjne jako naturalną część pracy z kolekcjami.

Najczęściej używane operacje są bardzo podobne do tych z Javy:

* `filter`,
* `map`,
* `flatMap`,
* `groupBy`,
* `associate`,
* `reduce`,
* `fold`,
* `sortedBy`,
* `distinct`,
* `any`,
* `all`.

Kotlinowe API jest zwykle bardziej zwarte i mniej techniczne.

---

# Lambdy

Programowanie funkcyjne w obu językach opiera się mocno na lambdach.

Java zapisuje lambdy w stylu:

```java
user -> user.getAge() >= 18
```

Kotlin wykorzystuje składnię:

```kotlin
{ user -> user.age >= 18 }
```

oraz bardzo często skróconą postać:

```kotlin
{ it.age >= 18 }
```

Kotlinowe lambdy są zwykle krótsze i bardziej naturalne.

Jednocześnie nadmierne używanie skrótu `it` może pogarszać czytelność, szczególnie przy zagnieżdżonych transformacjach.

---

# Declarative vs Imperative Style

Jednym z głównych celów stylu funkcyjnego jest przejście od stylu imperatywnego do deklaratywnego.

Styl imperatywny skupia się na krokach wykonywania:

```text
- utwórz listę,
- przejdź po elementach,
- sprawdź warunek,
- dodaj wynik,
- zwróć listę.
```

Styl deklaratywny skupia się bardziej na samym celu:

```text
- przefiltruj użytkowników,
- przemapuj ich na nazwy,
- zwróć wynik.
```

To bardzo wpływa na czytelność kodu.

W Kotlinie styl deklaratywny jest zwykle bardziej naturalny i mniej rozwlekły.

Java również wspiera taki styl, ale Stream API nadal jest bardziej techniczną warstwą dodaną do istniejącego języka obiektowego.

---

# Immutability i Functional Style

Programowanie funkcyjne bardzo często wiąże się z niemutowalnością.

Immutable collections oraz immutable objects zmniejszają liczbę efektów ubocznych i upraszczają reasoning o kodzie.

Kotlin bardzo mocno promuje taki styl przez:

* `val`,
* immutable collections,
* data classes,
* copy(),
* expression-based syntax.

Java również może pracować w immutable style, ale historycznie była bardziej mutowalna.

Nowoczesna Java coraz mocniej wspiera:

* immutable collections,
* records,
* functional transformations.

Mimo to Kotlin nadal lepiej integruje immutable programming z codziennym stylem pracy.

---

# FlatMap i transformacje zagnieżdżone

Jednym z bardziej charakterystycznych mechanizmów programowania funkcyjnego jest `flatMap`.

Pozwala on spłaszczać zagnieżdżone struktury danych.

Przykładowo:

* użytkownik posiada wiele ról,
* zamówienie posiada wiele produktów,
* grupa zawiera wielu użytkowników.

Zamiast otrzymywać strukturę:

```text
List<List<Role>>
```

można uzyskać:

```text
List<Role>
```

Zarówno Java, jak i Kotlin wspierają `flatMap`, ale Kotlinowy zapis jest zwykle bardziej zwarty.

W Javie bardziej rozbudowane transformacje potrafią szybko stać się trudne do czytania.

---

# Grouping i agregacja danych

Programowanie funkcyjne bardzo dobrze nadaje się do grupowania danych.

Java wykorzystuje w tym celu głównie:

```java
Collectors.groupingBy(...)
```

Kotlin oferuje:

```kotlin
groupBy { ... }
```

Różnica ponownie polega głównie na ergonomii.

Java daje bardzo dużą elastyczność collectorów, ale bardziej złożone grupowanie może być trudniejsze do zapisania i zrozumienia.

Kotlin zwykle pozwala osiągnąć podobny efekt mniejszą ilością kodu.

---

# Functional Interfaces i Higher-Order Functions

Java wykorzystuje functional interfaces, takie jak:

* `Function`,
* `Predicate`,
* `Consumer`,
* `Supplier`.

Kotlin wspiera higher-order functions bezpośrednio w języku.

Funkcja może być przekazywana jako parametr bardzo naturalnie:

```kotlin
fun process(block: () -> Unit)
```

W Kotlinie funkcje są bardziej „first-class citizens”.

To bardzo wpływa na projektowanie API i DSL-i.

---

# Method References vs Function References

Java wspiera method references:

```java
User::getFullName
```

Kotlin posiada podobny mechanizm:

```kotlin
User::fullName
```

oraz references do funkcji:

```kotlin
::calculateTax
```

Kotlin integruje ten model bardziej spójnie z właściwościami i funkcjami języka.

---

# Lazy Evaluation

Stream API w Javie jest lazy.

Oznacza to, że operacje nie są wykonywane od razu, ale dopiero po operacji terminalnej:

```java
collect(...)
```

Kotlinowe kolekcje są domyślnie eager.

Oznacza to, że każda operacja tworzy pośredni wynik.

Jeżeli potrzebna jest lazy evaluation, Kotlin wykorzystuje `Sequence`:

```kotlin
users.asSequence()
```

To ważna różnica wydajnościowa.

W przypadku bardzo dużych kolekcji albo długich pipeline’ów Java Streams mogą być bardziej efektywne bezpośrednio po wyjęciu z pudełka.

Kotlin daje większą prostotę dla typowych przypadków, ale wymaga świadomego użycia `Sequence` przy bardziej wymagających operacjach.

---

# Side Effects

Jednym z głównych założeń programowania funkcyjnego jest ograniczanie efektów ubocznych.

Idealnie funkcja:

* przyjmuje dane,
* zwraca wynik,
* nie modyfikuje zewnętrznego stanu.

W praktyce backendowej pełna eliminacja side effects jest niemożliwa, ponieważ aplikacje:

* zapisują dane,
* wykonują requesty HTTP,
* komunikują się z bazą,
* publikują eventy.

Jednak ograniczanie side effects poprawia:

* testowalność,
* przewidywalność,
* bezpieczeństwo współbieżne.

Kotlin zwykle mocniej zachęca do takiego stylu przez większy nacisk na immutable programming.

---

# Czytelność kodu funkcyjnego

Programowanie funkcyjne może znacząco poprawić czytelność, ale tylko do pewnego momentu.

Zbyt długie pipeline’y:

```text
filter → map → flatMap → groupBy → associate → fold
```

mogą stać się trudne do zrozumienia.

Dotyczy to zarówno Javy, jak i Kotlina.

Kotlin daje bardziej kompaktową składnię, ale przez to łatwiej napisać kod, który wygląda „sprytnie”, ale jest trudny do utrzymania.

Java jest bardziej rozwlekła, ale czasem właśnie przez tę jawność łatwiej śledzić przepływ danych.

---

# Java i Kotlin jako języki wieloparadygmatowe

Ani Java, ani Kotlin nie są czysto funkcyjnymi językami.

Oba są językami wieloparadygmatowymi.

Oznacza to, że można w nich łączyć:

* programowanie obiektowe,
* funkcyjne,
* imperatywne,
* reaktywne.

Kotlin ma jednak znacznie silniejsze wpływy funkcyjne.

Widać to między innymi w:

* lambdach,
* immutable style,
* extension functions,
* higher-order functions,
* scope functions,
* expression-oriented syntax.

Java rozwija się bardziej konserwatywnie i stopniowo dodaje nowoczesne mechanizmy funkcyjne.

---

# Podsumowanie

Programowanie funkcyjne w Javie i Kotlinie rozwiązuje podobne problemy, ale oba języki robią to innym stylem.

Java wykorzystuje przede wszystkim Stream API oraz functional interfaces. Daje to bardzo duże możliwości transformacji danych i pozwala odejść od dużej liczby manualnych pętli.

Kotlin integruje styl funkcyjny dużo głębiej z samym językiem. Operacje na kolekcjach, lambdy, immutable programming i higher-order functions są bardziej naturalną częścią codziennego kodu.

Największa praktyczna różnica dotyczy ergonomii.

Kotlin zwykle pozwala pisać krótszy, bardziej deklaratywny kod. Java pozostaje bardziej jawna i techniczna, ale dla wielu zespołów enterprise taka jawność jest zaletą.

Nowoczesna Java znacząco zmniejszyła dystans do Kotlina, jednak Kotlin nadal oferuje bardziej spójne doświadczenie dla programowania funkcyjnego i declarative data processing.
