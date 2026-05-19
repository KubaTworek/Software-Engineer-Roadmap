# Domain Modeling — Java vs Kotlin

## Wprowadzenie

Ten moduł skupia się na różnicach pomiędzy Javą i Kotlinem w kontekście modelowania domeny. Nie chodzi tutaj o konkretne klasy biznesowe, ale o sam sposób wyrażania pojęć domenowych w kodzie.

Modelowanie domeny polega na odwzorowaniu realnych konceptów biznesowych przy pomocy typów, relacji oraz ograniczeń językowych. Dobrze zaprojektowany model domenowy powinien być:

* czytelny,
* bezpieczny typowo,
* trudny do użycia w niepoprawny sposób,
* odporny na niepoprawne stany,
* łatwy do rozwijania.

Java i Kotlin rozwiązują te problemy podobnymi mechanizmami, ale ich filozofia jest wyraźnie różna. Java stawia bardziej na jawność i rozdzielenie odpowiedzialności, natomiast Kotlin mocno skupia się na zwięzłości oraz ergonomii.

---

# Modelowanie danych

Najbardziej podstawowym elementem modelowania domeny są obiekty reprezentujące dane.

W klasycznej Javie przez wiele lat tworzenie prostych modeli domenowych wiązało się z dużą ilością boilerplate’u:

* pola,
* konstruktor,
* gettery,
* equals,
* hashCode,
* toString.

Powodowało to sytuację, w której duża część kodu modelu domenowego była techniczną infrastrukturą, a nie właściwą logiką biznesową.

Nowoczesna Java znacząco poprawiła ten obszar przez wprowadzenie `record`. Record pozwala tworzyć niemutowalne modele danych przy dużo mniejszej ilości kodu.

Kotlin od początku był projektowany z takim stylem programowania. `data class` jest centralnym elementem modelowania danych w Kotlinie i automatycznie generuje większość metod technicznych.

Najważniejsza różnica nie polega jednak wyłącznie na ilości kodu. Kotlin traktuje modele danych jako naturalny element języka. Właściwości są deklarowane bezpośrednio w konstruktorze, a operacje takie jak kopiowanie obiektu czy porównywanie są integralną częścią modelu.

W praktyce daje to bardziej zwarty i czytelny kod domenowy.

---

# Niemutowalność

Niemutowalność jest jednym z najważniejszych aspektów nowoczesnego modelowania domeny.

Immutable object to obiekt, którego stan nie zmienia się po utworzeniu. Takie podejście zmniejsza liczbę efektów ubocznych i upraszcza reasoning o systemie.

Kotlin bardzo mocno promuje immutable style przez:

* `val`,
* `data class`,
* immutable collections,
* `copy()`.

W Javie również można tworzyć immutable models, ale wymaga to bardziej świadomego pilnowania:

* `final`,
* braku setterów,
* defensywnego kopiowania kolekcji,
* odpowiedniej konstrukcji klasy.

Records bardzo pomagają w tym obszarze, ale Kotlin nadal daje bardziej naturalny model pracy z immutable state.

To jest szczególnie ważne w:

* systemach wielowątkowych,
* event-driven architecture,
* reactive systems,
* CQRS,
* async programming.

---

# Reprezentowanie stanów domenowych

Jednym z najważniejszych problemów modelowania domeny jest reprezentowanie stanów oraz wariantów zachowania.

Najprostszym przypadkiem jest zamknięty zbiór prostych stanów, takich jak:

* CREATED,
* PAID,
* SHIPPED,
* CANCELLED.

Do takich sytuacji bardzo dobrze nadają się enumy.

Java wykorzystuje `enum`, a Kotlin `enum class`. Koncepcyjnie oba rozwiązania są bardzo podobne.

Problem pojawia się wtedy, gdy różne warianty tego samego konceptu wymagają różnych danych.

Przykładowo wynik operacji może być:

* sukcesem,
* błędem,
* stanem oczekującym.

Każdy z tych wariantów może posiadać inne pola.

W starszym stylu programowania często rozwiązywano to przez:

* nullable fields,
* boolean flags,
* string status,
* duże klasy z wieloma opcjonalnymi polami.

Takie modele są niebezpieczne, ponieważ dopuszczają niepoprawne kombinacje danych.

---

# Sealed Classes i zamknięte hierarchie typów

Nowoczesne modelowanie domeny bardzo często wykorzystuje zamknięte hierarchie typów.

Java oferuje:

* `sealed interface`,
* `sealed class`.

Kotlin oferuje:

* `sealed class`,
* `sealed interface`.

Idea jest taka sama: określenie skończonego zbioru możliwych wariantów.

To bardzo ważne z perspektywy bezpieczeństwa typów.

Zamiast modelować wynik operacji jako:

```text
status: String
errorMessage: nullable
transactionId: nullable
```

można stworzyć osobne warianty reprezentujące:

* sukces,
* błąd,
* oczekiwanie.

Każdy wariant przechowuje wyłącznie dane, które są dla niego poprawne.

Takie podejście:

* eliminuje wiele nullable fields,
* utrudnia tworzenie niepoprawnych stanów,
* poprawia czytelność domeny,
* zwiększa bezpieczeństwo kompilatora.

---

# Pattern Matching i obsługa wariantów

Kiedy model domenowy posiada wiele wariantów, ważne staje się wygodne ich obsługiwanie.

Java wykorzystuje w tym celu:

* `instanceof`,
* pattern matching,
* switch pattern matching.

Kotlin wykorzystuje:

* `when`,
* smart casts.

Największa różnica polega na ergonomii.

Kotlinowe `when` w połączeniu z sealed classes daje bardzo naturalny model pracy. Kompilator zna wszystkie możliwe warianty i może wymuszać kompletność obsługi.

To oznacza, że dodanie nowego wariantu może automatycznie wygenerować błędy kompilacji w miejscach, które należy zaktualizować.

Jest to bardzo ważne w dużych systemach biznesowych, ponieważ zmniejsza ryzyko pominięcia nowego przypadku.

Nowoczesna Java również rozwija się mocno w tym kierunku, ale Kotlin nadal oferuje bardziej spójny model językowy.

---

# Walidacja modelu domenowego

Dobry model domenowy powinien chronić własną poprawność.

Obiekt domenowy nie powinien istnieć w niepoprawnym stanie.

Przykładowo:

* identyfikator nie powinien być pusty,
* kwota nie powinna być ujemna,
* nazwa użytkownika nie powinna być nullem.

W Javie walidacja zwykle znajduje się:

* w konstruktorze,
* compact constructorze rekordu,
* factory methods.

W Kotlinie naturalnym miejscem jest blok `init`.

Najważniejsza różnica nie polega na samym mechanizmie, ale na ergonomii.

Kotlin zwykle zapisuje takie ograniczenia bardziej zwięźle, dzięki funkcjom takim jak:

```kotlin
require(...)
check(...)
```

Java pozostaje bardziej jawna i imperatywna.

---

# Copy i ewolucja stanu

W immutable domain models nie zmienia się istniejących obiektów. Zamiast tego tworzy się nowe wersje obiektu.

Kotlin bardzo mocno wspiera taki styl przez automatycznie generowaną funkcję `copy()`.

Pozwala ona stworzyć nową wersję obiektu z wybraną zmianą bez przepisywania wszystkich pól.

To jest bardzo wygodne przy:

* state management,
* event sourcing,
* CQRS,
* reactive programming.

Java records również wspierają immutable style, ale aktualizacja obiektu wymaga zwykle ręcznego utworzenia nowej instancji.

Java jest tutaj bardziej jawna, ale mniej ergonomiczna.

---

# Organizacja modelu domenowego

Java zwykle promuje bardziej rozproszoną strukturę.

Każdy wariant domenowy często znajduje się w osobnym pliku. Dobrze pasuje to do dużych systemów enterprise, gdzie istotna jest wyraźna separacja odpowiedzialności.

Kotlin pozwala grupować wiele wariantów bezpośrednio wewnątrz sealed class.

Dzięki temu cała hierarchia może być widoczna w jednym miejscu.

To zwiększa czytelność małych i średnich modeli domenowych, ale w bardzo dużych systemach czasem prowadzi do zbyt dużych plików.

W praktyce oba podejścia mają sens i zależą od skali projektu.

---

# Java i Kotlin w nowoczesnym modelowaniu domeny

Jeszcze kilka lat temu Kotlin miał znacznie większą przewagę w obszarze domain modeling.

Nowoczesna Java bardzo zmniejszyła tę różnicę przez:

* records,
* sealed classes,
* pattern matching,
* switch expressions.

Dzięki temu Java potrafi dziś modelować domenę znacznie bardziej bezpiecznie i nowocześnie niż starsze wersje języka.

Mimo to Kotlin nadal oferuje bardziej kompaktowy i spójny model pracy z:

* immutable objects,
* sealed hierarchies,
* state transitions,
* domain variants.

---

# Podsumowanie

Najważniejszą różnicą pomiędzy Javą i Kotlinem w kontekście modelowania domeny nie są pojedyncze feature’y, ale filozofia języka.

Java skupia się bardziej na:

* jawności,
* rozdzieleniu odpowiedzialności,
* stabilności,
* konserwatywnym rozwoju języka.

Kotlin skupia się bardziej na:

* ekspresyjności,
* redukcji boilerplate’u,
* ergonomii modelowania,
* bezpieczeństwie typów,
* immutable programming.

Oba języki pozwalają dziś budować bardzo dobre modele domenowe. Kotlin zwykle daje bardziej zwarty i ergonomiczny kod, natomiast Java pozostaje bardzo przewidywalna i dobrze pasuje do dużych, konserwatywnych systemów enterprise.

W praktyce wybór pomiędzy nimi zależy bardziej od charakteru projektu i zespołu niż od samych możliwości języka.
