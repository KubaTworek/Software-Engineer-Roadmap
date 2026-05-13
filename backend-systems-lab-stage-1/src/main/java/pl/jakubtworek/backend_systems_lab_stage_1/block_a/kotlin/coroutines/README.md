# Kotlin Coroutines vs Java Async — README

## Wprowadzenie

Ten moduł opisuje różnice pomiędzy podejściem Kotlina i Javy do programowania asynchronicznego oraz współbieżnego. Skupia się na dwóch głównych przypadkach: prostym wywołaniu asynchronicznym oraz równoległym pobieraniu danych z kilku niezależnych źródeł.

Celem nie jest pokazanie pełnej teorii współbieżności, ale praktyczne porównanie kodu, który programista może spotkać w aplikacji backendowej. Przykłady pokazują, jak Kotlin wykorzystuje `suspend functions`, `coroutineScope`, `async` i `await`, a Java korzysta z `CompletableFuture` oraz `ExecutorService`.

---

# Async

## Idea

W katalogu `async` przedstawiony jest prosty przypadek: aplikacja musi pobrać użytkownika z zewnętrznego źródła, na przykład z API, bazy danych albo innego serwisu.

W Javie taki kod często modelowany jest przez `CompletableFuture`. Metoda nie zwraca bezpośrednio obiektu `User`, ale opakowuje przyszły wynik w `CompletableFuture<User>`.

W Kotlinie ten sam koncept jest zwykle reprezentowany przez funkcję oznaczoną jako `suspend`. Taka funkcja wygląda tak, jakby zwracała zwykły obiekt, na przykład `User`, ale może zostać wstrzymana bez blokowania wątku.

To jest jedna z najważniejszych różnic stylistycznych pomiędzy tymi językami.

---

## Podejście w Javie

Java zwykle pokazuje asynchroniczność bezpośrednio w typie zwracanym przez metodę.

Przykładowa metoda może wyglądać koncepcyjnie tak:

```java
CompletableFuture<User> getUserAsync(String userId)
```

Oznacza to, że wynik nie jest dostępny od razu. Kod wywołujący musi kontynuować pracę z `CompletableFuture`, używając metod takich jak:

* `thenApply`,
* `thenCompose`,
* `thenCombine`,
* `exceptionally`,
* `join`,
* `get`.

Taki styl jest bardzo jawny. Programista widzi, że operacja jest asynchroniczna, bo typ zwracany to `CompletableFuture`. Jednocześnie kod może stać się mniej czytelny, gdy pojawia się kilka zależnych od siebie kroków.

Przykład transformacji wyniku w Javie:

```java
return getUserAsync(userId)
        .thenApply(User::getName);
```

Kod jest poprawny i elastyczny, ale logika biznesowa zaczyna być zapisana jako łańcuch operacji na przyszłych wynikach.

---

## Podejście w Kotlinie

W Kotlinie asynchroniczność często nie jest widoczna w typie zwracanym, tylko w słowie kluczowym `suspend`.

Przykład:

```kotlin
suspend fun getUser(userId: String): User
```

Taka funkcja nadal zwraca `User`, ale może zawiesić wykonanie coroutine. Z punktu widzenia czytelności kodu jest to bardzo wygodne, ponieważ asynchroniczny kod może wyglądać podobnie do kodu sekwencyjnego.

Przykład:

```kotlin
val user = getUser(userId)
return user.name
```

Nie ma tutaj łańcucha `thenApply`. Kod jest prostszy do czytania, szczególnie wtedy, gdy kilka operacji zależy od siebie.

Warto jednak pamiętać, że `suspend` nie oznacza automatycznie uruchomienia kodu na osobnym wątku. Oznacza jedynie, że funkcja może zostać zawieszona i wznowiona w odpowiednim kontekście coroutine.

---

## Najważniejsza różnica w async

Najważniejsza różnica polega na tym, że Java eksponuje asynchroniczność przez typ `CompletableFuture`, a Kotlin przez `suspend`.

Java jest bardziej jawna i daje bardzo precyzyjną kontrolę nad mechaniką asynchroniczności. Kotlin jest bardziej ergonomiczny i pozwala pisać kod, który wygląda jak zwykły przepływ sekwencyjny.

W prostych przypadkach różnica może wydawać się niewielka. W bardziej rozbudowanych przypadkach Kotlin zwykle daje czytelniejszy kod, szczególnie gdy operacje są zależne jedna od drugiej.

---

# Parallel

## Idea

Katalog `parallel` pokazuje bardziej praktyczny przypadek. Aplikacja musi pobrać kilka niezależnych danych, na przykład użytkownika oraz jego zamówienia, a następnie złożyć z nich jeden obiekt `Dashboard`.

Dane użytkownika i zamówienia nie zależą od siebie, więc mogą zostać pobrane równolegle. Dzięki temu całkowity czas wykonania może być krótszy niż przy sekwencyjnym pobieraniu danych.

---

## Podejście w Javie

W Javie typowym rozwiązaniem jest utworzenie dwóch obiektów `CompletableFuture` i połączenie ich za pomocą `thenCombine`.

Koncepcyjnie wygląda to tak:

```java
CompletableFuture<User> userFuture = ...;
CompletableFuture<List<Order>> ordersFuture = ...;

return userFuture.thenCombine(
        ordersFuture,
        Dashboard::new
);
```

Ten kod jasno pokazuje, że uruchamiane są dwie niezależne operacje asynchroniczne. `thenCombine` czeka na zakończenie obu i tworzy wynik końcowy.

W praktyce Java wymaga też zwykle `ExecutorService`, który określa, gdzie mają być wykonywane zadania.

To daje dużą kontrolę nad wątkami, ale oznacza więcej kodu infrastrukturalnego. Programista musi świadomie zarządzać executorem, jego cyklem życia oraz strategią wykonywania zadań.

---

## Podejście w Kotlinie

W Kotlinie równoległe operacje są często zapisywane przy użyciu `coroutineScope`, `async` i `await`.

Przykład koncepcyjny:

```kotlin
suspend fun loadDashboard(userId: String): Dashboard = coroutineScope {
    val userDeferred = async {
        userApiClient.fetchUserById(userId)
    }

    val ordersDeferred = async {
        orderApiClient.fetchOrdersByUserId(userId)
    }

    Dashboard(
        user = userDeferred.await(),
        orders = ordersDeferred.await()
    )
}
```

`async` uruchamia współbieżną operację i zwraca `Deferred`. `await` czeka na wynik tej operacji. Kod nadal jest czytany od góry do dołu, mimo że operacje mogą wykonywać się równolegle.

W tym przykładzie ważne jest użycie `coroutineScope`. Nie jest to tylko element składniowy. `coroutineScope` zapewnia structured concurrency, czyli wiąże cykl życia zadań potomnych z zakresem nadrzędnym.

Jeżeli jedna z operacji zakończy się błędem, pozostałe mogą zostać anulowane. Dzięki temu łatwiej uniknąć sytuacji, w której jedna część operacji już się nie powiodła, ale inne zadania nadal działają w tle.

---

# Structured Concurrency

Structured concurrency jest jednym z najważniejszych powodów, dla których coroutines są wygodne w większych aplikacjach.

W klasycznym modelu asynchronicznym łatwo stworzyć zadanie, które działa niezależnie od miejsca, z którego zostało uruchomione. Może to prowadzić do wycieków zasobów, trudnego debugowania i niejasnych zależności pomiędzy zadaniami.

Kotlin promuje podejście, w którym każda coroutine działa w określonym scope. Dzięki temu łatwiej odpowiedzieć na pytania:

* kto uruchomił zadanie,
* kiedy zadanie powinno zostać zakończone,
* co się stanie, gdy zadanie nadrzędne zostanie anulowane,
* jak propagować błędy.

`coroutineScope` czeka na zakończenie wszystkich coroutine uruchomionych wewnątrz niego. Jeśli jedna z nich rzuci wyjątek, scope zakończy się błędem, a pozostałe zadania zostaną anulowane.

To podejście jest bardzo przydatne w backendzie, gdzie pojedynczy request HTTP często uruchamia kilka zapytań do różnych źródeł danych.

---

# Threads vs Coroutines

Coroutine nie jest tym samym co thread.

Thread jest zasobem systemowym zarządzanym przez system operacyjny. Tworzenie dużej liczby wątków może być kosztowne.

Coroutine jest lżejszą abstrakcją. Wiele coroutine może działać na mniejszej liczbie wątków. Gdy coroutine czeka na wynik operacji asynchronicznej, może zostać zawieszona, a thread może zostać użyty do innej pracy.

To nie oznacza, że coroutines są zawsze szybsze. Oznacza raczej, że dają inny model organizacji współbieżności.

Java również mocno się rozwinęła w tym obszarze. Virtual threads znacząco zmniejszają koszt blokującego stylu programowania i sprawiają, że nowoczesna Java jest dużo bardziej konkurencyjna wobec coroutine-based code.

Dlatego porównanie nie powinno być uproszczone do stwierdzenia, że coroutines są zawsze lepsze. Bardziej precyzyjnie: coroutines są bardzo ergonomiczne, szczególnie gdy zależy nam na structured concurrency, anulowaniu zadań i czytelnym zapisie przepływu asynchronicznego.

---

# Typowe problemy

Najczęstszym problemem jest próba wywołania funkcji `suspend` ze zwykłej funkcji. Funkcje `suspend` muszą być wywoływane z innej funkcji `suspend` albo z kontekstu coroutine, na przykład przez `runBlocking` w prostych przykładach testowych.

Przykład uruchomienia testowego:

```kotlin
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val dashboard = dashboardService.loadDashboard("U-1")
    println(dashboard)
}
```

`runBlocking` jest przydatne w przykładach, testach lub prostych programach demonstracyjnych. W aplikacjach backendowych zwykle nie powinno być nadużywane, ponieważ blokuje aktualny wątek.

---

# Podsumowanie

Moduł `coroutines` pokazuje, że Kotlin i Java rozwiązują podobne problemy w inny sposób.

Java opiera się na bardziej jawnym modelu, w którym asynchroniczność jest widoczna w typach takich jak `CompletableFuture`. Ten styl daje dużą kontrolę, ale może prowadzić do bardziej technicznego i mniej liniowego kodu.

Kotlin wykorzystuje `suspend functions` i coroutines, dzięki czemu kod asynchroniczny może wyglądać podobnie do zwykłego kodu sekwencyjnego. Dodatkowo `coroutineScope`, `async` i `await` pozwalają wygodnie opisywać równoległe operacje oraz ich cykl życia.

Najważniejsza praktyczna różnica polega na czytelności i strukturze kodu. Java pokazuje mechanikę asynchroniczności bardziej bezpośrednio, natomiast Kotlin ukrywa część tej mechaniki za modelem coroutine i pozwala skupić się bardziej na przepływie biznesowym.

W prostych przykładach oba podejścia są poprawne. W bardziej rozbudowanych przepływach Kotlin zwykle daje bardziej czytelny kod, ale Java pozostaje bardzo silna, szczególnie w projektach wykorzystujących nowoczesne mechanizmy współbieżności.
