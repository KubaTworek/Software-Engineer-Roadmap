# Kotlin vs Java — Podstawowe Koncepty Językowe

# Readability i redukcja boilerplate’u

Jedną z najbardziej zauważalnych różnic pomiędzy Javą i Kotlinem jest ilość kodu potrzebna do zapisania prostego modelu domenowego.

W Javie nawet niewielka klasa bardzo często wymaga:

* jawnych pól,
* konstruktora,
* getterów,
* metod `equals()` i `hashCode()`,
* implementacji `toString()`,
* dodatkowych metod pomocniczych.

Powoduje to dużą ilość boilerplate’u, czyli kodu technicznego, który nie wnosi logiki biznesowej, ale jest wymagany przez język.

Kotlin został zaprojektowany tak, aby ograniczyć ten problem. Wykorzystuje między innymi:

* `data class`,
* właściwości (`val`, `var`),
* automatyczne generowanie metod,
* computed properties.

Dzięki temu model domenowy może być znacznie krótszy i bardziej czytelny.

Przykładowo:

```kotlin
val fullName: String
    get() = "$firstName $lastName"
```

w Kotlinie zastępuje klasyczny getter znany z Javy.

Nowoczesna Java częściowo zmniejsza tę różnicę przez wprowadzenie `record`, jednak Kotlin nadal pozostaje bardziej zwięzły i ekspresyjny.

---

# Null Safety

Obsługa wartości `null` jest jedną z największych praktycznych różnic pomiędzy tymi językami.

W Javie `null` jest dozwolony praktycznie wszędzie. Oznacza to, że wiele błędów związanych z brakiem wartości pojawia się dopiero podczas działania programu.

Najbardziej klasycznym przykładem jest:

```text
NullPointerException
```

Java oferuje mechanizmy ograniczające ten problem, takie jak:

* `Optional`,
* adnotacje `@Nullable` i `@NotNull`,
* manualne null checki,

jednak nie są one integralną częścią systemu typów.

Kotlin podchodzi do tego inaczej. Nullability jest częścią języka.

```kotlin
val email: String?
```

znaczy coś zupełnie innego niż:

```kotlin
val email: String
```

Kompilator wymusza jawne obsłużenie nullable values.

Przykład:

```kotlin
email?.length ?: 0
```

Dzięki temu wiele potencjalnych błędów jest wykrywanych już podczas kompilacji, a nie dopiero w runtime.

To podejście znacząco wpływa na bezpieczeństwo kodu, szczególnie w dużych projektach backendowych.

---

# Extension Functions

Extension functions są jednym z najbardziej charakterystycznych elementów Kotlina.

Pozwalają dodawać nowe funkcje do istniejących klas bez:

* dziedziczenia,
* modyfikowania oryginalnego kodu,
* tworzenia utility classes.

Przykład:

```kotlin
fun String.isEmail(): Boolean
```

pozwala wywoływać:

```kotlin
"test@gmail.com".isEmail()
```

Kod wygląda tak, jakby metoda należała bezpośrednio do klasy `String`.

W Javie podobny efekt zwykle realizuje się przez utility class:

```java
StringUtils.isEmail(value)
```

Różnica może wydawać się niewielka, ale w praktyce mocno wpływa na styl projektowania API i czytelność kodu.

Kotlin pozwala tworzyć bardziej naturalne i płynne API.

---

# Immutability

Kotlin od początku był projektowany z silnym naciskiem na niemutowalność.

Najbardziej podstawowym przykładem jest różnica pomiędzy:

```kotlin
val
```

oraz:

```kotlin
var
```

`val` tworzy read-only reference i jest domyślnym wyborem w idiomatycznym Kotlinie.

Java historycznie była bardziej mutowalna. Chociaż posiada `final`, przez wiele lat dominował styl oparty na mutable objects.

Dopiero nowoczesna Java zaczęła mocniej promować:

* immutable collections,
* records,
* functional style.

Niemutowalność jest bardzo ważna szczególnie w:

* programowaniu współbieżnym,
* architekturze reaktywnej,
* systemach wielowątkowych,
* modelowaniu domeny.

Kod immutable jest zwykle bardziej przewidywalny i łatwiejszy do utrzymania.

---

# Smart Casts

Kotlin upraszcza pracę z typami dzięki mechanizmowi smart casts.

Po sprawdzeniu typu:

```kotlin
if (user is AdminUser)
```

kompilator automatycznie traktuje obiekt jako `AdminUser`.

Nie jest wymagane ręczne rzutowanie.

W starszych wersjach Javy konieczne było:

```java
((AdminUser) user)
```

Nowoczesna Java wspiera pattern matching dla `instanceof`, co częściowo zmniejsza tę różnicę:

```java
if (user instanceof AdminUser admin)
```

Mimo to Kotlin nadal robi to bardziej naturalnie i płynnie.

Smart casts poprawiają czytelność oraz redukują ilość kodu technicznego.

---

# Default Arguments

Kotlin wspiera domyślne wartości parametrów funkcji.

Przykład:

```kotlin
fun createUser(name: String, active: Boolean = true)
```

Pozwala to wywoływać funkcję bez podawania wszystkich argumentów.

W Javie podobny efekt zwykle wymaga:

* overloaded methods,
* builder pattern,
* dodatkowych konstruktorów.

Przy większej liczbie parametrów różnica w ilości kodu staje się bardzo wyraźna.

Default arguments upraszczają API oraz ograniczają liczbę pomocniczych metod.

---

# Named Arguments

Named arguments są naturalnym rozszerzeniem default arguments.

Pozwalają jawnie określić znaczenie parametrów podczas wywołania:

```kotlin
createUser(
    firstName = "John",
    age = 30
)
```

Poprawia to czytelność kodu szczególnie wtedy, gdy funkcja posiada wiele parametrów tego samego typu.

Java nie posiada named arguments. Znaczenie parametrów wynika wyłącznie z kolejności.

To jedna z tych różnic, które wydają się małe, ale w dużych projektach znacząco wpływają na readability.

---

# Scope Functions

Scope functions są bardzo charakterystycznym elementem idiomatycznego Kotlina.

Najważniejsze z nich to:

* `let`
* `apply`
* `also`
* `run`
* `with`

Pozwalają wykonywać operacje na obiekcie wewnątrz określonego scope.

Przykład:

```kotlin
val user = User().apply {
    name = "John"
}
```

Kod może być dzięki temu bardziej zwięzły i płynny.

Java zwykle wykorzystuje bardziej klasyczny styl imperatywny.

Jednocześnie scope functions są jednym z najczęściej nadużywanych mechanizmów Kotlina. Nadmierne zagnieżdżanie `let`, `also` i `run` może znacząco pogarszać czytelność.

Dlatego ich używanie wymaga dyscypliny i spójnych standardów zespołowych.

---

# Delegated Properties

Delegated properties pozwalają delegować logikę właściwości do gotowych mechanizmów.

Najbardziej znanym przykładem jest:

```kotlin
val config by lazy {
    loadConfig()
}
```

Wartość zostanie obliczona dopiero przy pierwszym użyciu.

Kotlin oferuje także:

* `observable`,
* `vetoable`,
* custom delegates.

W Javie podobne rozwiązania wymagają zwykle manualnej implementacji lazy loading lub observer patterns.

Delegated properties upraszczają zarządzanie stanem oraz redukują kod infrastrukturalny.

---

# Operator Overloading

Kotlin pozwala przeciążać operatory.

Przykład:

```kotlin
operator fun Money.plus(other: Money): Money
```

umożliwia zapis:

```kotlin
money1 + money2
```

Java nie wspiera operator overloading.

Zamiast tego stosowane są jawne metody:

```java
money1.add(money2)
```

Operator overloading może znacząco poprawiać czytelność w:

* matematyce,
* DSL-ach,
* modelach domenowych,
* bibliotekach numerycznych.

Jednocześnie jest to mechanizm, który bardzo łatwo nadużyć.

Zbyt agresywne przeciążanie operatorów może prowadzić do nieczytelnego kodu.

---

# Top-Level Functions

Kotlin pozwala definiować funkcje poza klasami.

Przykład:

```kotlin
fun calculateTax() { }
```

Nie ma potrzeby tworzenia utility classes.

W Javie każda metoda musi należeć do klasy.

Najczęściej prowadzi to do tworzenia klas typu:

```java
TaxUtils
StringUtils
DateUtils
```

Top-level functions upraszczają organizację helper methods oraz ograniczają sztuczne klasy techniczne.

---

# Reified Generics

Kotlin oferuje bardziej zaawansowaną pracę z typami generycznymi dzięki:

```kotlin
inline fun <reified T>
```

Pozwala to uzyskać dostęp do typu generycznego podczas działania programu.

Mechanizm jest bardzo przydatny między innymi przy:

* serializacji JSON,
* reflection,
* dependency injection,
* parserach,
* frameworkach backendowych.

Java wykorzystuje type erasure.

Oznacza to, że informacje o typach generycznych są usuwane podczas kompilacji.

Dlatego w Javie często trzeba jawnie przekazywać:

```java
Class<T>
```

lub bardziej złożone konstrukcje typu `TypeReference`.

To jeden z obszarów, gdzie Kotlin daje realnie bardziej zaawansowane możliwości językowe.
