# Case 5 — Polymorphism vs JIT Inlining

## Wprowadzenie

Jednym z najważniejszych mechanizmów wpływających na wydajność JVM jest:
## inlining

czyli zastępowanie wywołania metody jej rzeczywistą implementacją bez wykonywania klasycznego call dispatch.

Inlining jest fundamentem większości dalszych optymalizacji JIT:
- constant folding,
- dead-code elimination,
- scalar replacement,
- escape analysis,
- loop optimizations,
- vectorization.

Problem polega na tym, że:
> polimorfizm utrudnia inlining.

A dokładniej:
- utrudnia devirtualizację call-site,
- a bez devirtualizacji JVM często nie może inline’ować metody.

Dlatego zrozumienie:
- monomorphic,
- bimorphic,
- megamorphic call-sites

jest absolutnie kluczowe dla poprawnego modelu mentalnego HotSpot JVM.

---

# Virtual call — co naprawdę dzieje się przy wywołaniu metody

W Javie:

```java
operation.apply(value);
```

bardzo często nie jest zwykłym wywołaniem funkcji.

Jeżeli typ runtime obiektu nie jest znany statycznie, JVM musi:
- sprawdzić rzeczywisty typ obiektu,
- znaleźć odpowiednią implementację,
- wykonać dispatch dynamiczny.

To nazywa się:
## virtual dispatch

I właśnie tutaj zaczynają się problemy optymalizacyjne.

---

# Dlaczego inlining jest tak ważny

Inlining sam w sobie oszczędza:
- koszt call stack,
- koszt dispatchu,
- część overheadu wywołania.

Ale to nie jest najważniejsze.

Najważniejsze jest to, że po inliningu:
- JIT widzi większy fragment programu jako jedną całość,
- może propagować stałe,
- usuwać zbędne warunki,
- upraszczać dane,
- eliminować obiekty.

Bardzo często:
> bez inliningu nie ma większości dalszych optymalizacji.

Dlatego:
- nieinline’owany kod zwykle optymalizuje się znacznie gorzej.

---

# Monomorphic call-site

Najprostszy przypadek wygląda tak:

```java
Operation op = new AddOperation();
op.apply(x);
```

Jeżeli JVM obserwuje, że:
- pod danym call-site zawsze pojawia się jeden typ runtime,

to call-site staje się:
## monomorphic

To idealny przypadek dla HotSpot.

JIT może:
- zdevirtualizować wywołanie,
- potraktować je jak direct call,
- inline’ować metodę,
- dalej optymalizować kod.

W praktyce:
- monomorphic call-sites często działają niemal tak szybko jak zwykłe funkcje proceduralne.

---

# Devirtualization

To kluczowe pojęcie.

## Devirtualization

oznacza:
> zamianę virtual call na direct call.

Jeżeli JVM potrafi udowodnić:
- jaki konkretny typ pojawi się runtime,
- jaka metoda zostanie wywołana,

to może usunąć koszt dynamicznego dispatchu.

Bez tego:
- pełny inlining często nie jest możliwy.

---

# Bimorphic call-site

Czasem JVM widzi:
- dwa typy runtime.

Przykład:

```java
if (...) {
    op = new AddOperation();
} else {
    op = new MultiplyOperation();
}
```

Wtedy call-site jest:
## bimorphic

To nadal stosunkowo dobry przypadek.

HotSpot potrafi często używać:
- type profiling,
- guarded inlining,
- szybkich runtime checks.

JIT może wygenerować coś podobnego do:

```text
if (type == AddOperation)
    inline add
else if (type == MultiplyOperation)
    inline multiply
else
    fallback
```

To nadal pozwala zachować sporą część optymalizacji.

---

# Megamorphic call-site

Najtrudniejszy przypadek pojawia się wtedy, gdy:
- pod jednym call-site pojawia się wiele różnych typów runtime.

Wtedy call-site staje się:
## megamorphic

To bardzo problematyczna sytuacja dla JIT.

Dlaczego?

Ponieważ:
- przewidywanie typu przestaje być skuteczne,
- guarded inlining robi się zbyt kosztowny,
- graph optymalizacyjny staje się zbyt złożony,
- liczba możliwych ścieżek eksploduje.

W efekcie JVM często:
- rezygnuje z devirtualizacji,
- nie inline’uje metod,
- zostawia virtual dispatch.

A wtedy:
- wiele dalszych optymalizacji przepada.

---

# Dlaczego „polimorfizm jest wolny” to uproszczenie

To bardzo ważne.

Sam polimorfizm nie jest automatycznie problemem.

Problemem jest:
- utrata możliwości optymalizacji.

Jeżeli call-site pozostaje:
- monomorphic,
- dobrze profilowany,

to HotSpot potrafi zoptymalizować kod bardzo agresywnie.

Dlatego:
> nowoczesny JVM potrafi świetnie radzić sobie z umiarkowanym polimorfizmem.

Problemy zaczynają się głównie przy:
- megamorphic dispatch,
- bardzo dynamicznych strukturach,
- frameworkach generujących wiele runtime typów,
- gorących ścieżkach z dużą liczbą virtual calls.

---

# Type profiling

HotSpot stale obserwuje:
- jakie typy pojawiają się pod call-site.

To właśnie:
## type profiling

JVM zbiera statystyki runtime:
- jak często pojawia się dany typ,
- czy call-site jest stabilny,
- czy warto inline’ować.

To oznacza, że:
- zachowanie programu runtime wpływa na decyzje optymalizacyjne JIT.

Dlatego:
- dwa logicznie podobne programy mogą optymalizować się zupełnie inaczej.

---

# Dlaczego „final” czasem pomaga

Metody:
- `final`,
- `private`,
- `static`

są łatwiejsze do optymalizacji, ponieważ:
- JVM zna ich target statycznie,
- nie wymagają virtual dispatch.

Ale to nie oznacza:
> „zawsze używaj final dla wydajności”

Nowoczesny HotSpot często potrafi zdevirtualizować również zwykłe virtual methods.

`final` pomaga głównie wtedy, gdy:
- analiza runtime jest utrudniona,
- call-site jest niestabilny,
- hierarchia klas jest dynamiczna.

---

# Inlining ma ograniczenia

To kolejny ważny aspekt.

JIT nie inline’uje wszystkiego.

Powody mogą być różne:
- metoda jest za duża,
- graph robi się zbyt kosztowny,
- call-site jest megamorphic,
- kod jest zbyt rzadko wykonywany,
- brakuje stabilnego profilu runtime.

Dlatego w logach JVM można zobaczyć:

```text
callee is too large
hot method too big
no static binding
megamorphic
```

To właśnie momenty, w których JIT uznał:
- że dalsza optymalizacja się nie opłaca.

---

# Dlaczego eksperyment trzeba uruchamiać wielokrotnie

Inlining jest:
- dynamiczny,
- profilowany runtime,
- zależny od rozgrzania JVM.

To oznacza:
- pierwsze uruchomienie może wyglądać inaczej,
- C1 i C2 mogą podejmować inne decyzje,
- niewielkie zmiany kodu mogą zmienić profile call-site.

Dlatego eksperymenty z JIT:
- wymagają warmupu,
- często wymagają wielu uruchomień,
- najlepiej analizować razem z:
    - `-XX:+PrintInlining`
    - `-XX:+PrintCompilation`

---

# Najważniejsza intuicja praktyczna

Najważniejszy wniosek brzmi:

> wydajność JVM bardzo często zależy nie od samego kodu źródłowego, lecz od tego, czy JIT potrafi zdevirtualizować i inline’ować hot path.

To dlatego:
- dwa bardzo podobne fragmenty kodu
  mogą mieć zupełnie inną wydajność.

I właśnie dlatego:
- zrozumienie call-site polymorphism
  jest jednym z fundamentów performance engineering na JVM.

---

# Najważniejsze wnioski

Najbardziej użyteczny model mentalny wygląda tak:

- virtual calls utrudniają optymalizacje JIT,
- devirtualization zamienia virtual call na direct call,
- monomorphic call-sites są idealnym przypadkiem dla inliningu,
- bimorphic call-sites nadal często optymalizują się dobrze,
- megamorphic call-sites są trudne dla HotSpot,
- bez inliningu wiele dalszych optymalizacji nie działa,
- JIT podejmuje decyzje na podstawie type profiling runtime,
- polimorfizm sam w sobie nie jest problemem,
- problemem jest utrata możliwości optymalizacji hot path,
- wydajność JVM bardzo często zależy od jakości inliningu.