# cancel

<!-- material-card:start -->
> [!IMPORTANT]
> **Karta materiału**
> - **Zakres:** `fundament`
> - **Uczy:** cancel.
> - **Typowy błąd:** Uznanie pojedynczego wyniku dotyczącego „cancel” za gwarancję bez sprawdzenia niezmiennika i failure modes.
> - **Najkrótsza weryfikacja:** `.\mvnw.cmd --batch-mode --no-transfer-progress "-Dtest=CancelTest" test`
> - **Role klas:** brak klasy-kontrprzykładu; pozostałe typy są minimalnymi modelami pojęć opisanych niżej.
> - **Granica:** Przykład dowodzi mechanizmu w opisanej granicy; bez testu infrastrukturalnego nie dowodzi zachowania wielu procesów ani konkretnej usługi.
<!-- material-card:end -->

## Kooperacyjne anulowanie zadań



## Cel przykładu

Ten pakiet pokazuje, że `Thread.interrupt()` nie zatrzymuje wątku siłowo. Jest sygnałem anulowania, na który kod zadania musi świadomie odpowiedzieć.

Porównaj:

- `CancellableTask` — kończy pracę po przerwaniu i zachowuje informację o przerwaniu,
- `BadCancellableTask` — połyka `InterruptedException`, przez co traci sygnał i działa bez końca,
- `CancelTest` — deterministycznie sprawdza obydwa zachowania bez pozostawiania niedaemonowego wątku.

## Jak działa przerwanie

Każdy wątek ma flagę przerwania. Wywołanie:

```java
worker.interrupt();
```

ustawia tę flagę. Nie gwarantuje natychmiastowego zakończenia pracy. Zadanie powinno:

1. okresowo sprawdzać `Thread.currentThread().isInterrupted()`, albo
2. korzystać z metod blokujących reagujących przez `InterruptedException`.

Do takich metod należą m.in. `Thread.sleep`, `Object.wait`, `Thread.join` oraz blokujące operacje kolejek z `java.util.concurrent`.

## `isInterrupted()` a `interrupted()`

- `Thread.currentThread().isInterrupted()` odczytuje flagę bez jej czyszczenia,
- `Thread.interrupted()` odczytuje i czyści flagę bieżącego wątku.

Przypadkowe użycie `Thread.interrupted()` może sprawić, że dalszy kod nie zobaczy żądania anulowania.

## Dlaczego przywracamy flagę

Gdy metoda blokująca rzuca `InterruptedException`, flaga przerwania zostaje wyczyszczona. Jeśli metoda nie może przekazać wyjątku wyżej, typowym rozwiązaniem jest jej przywrócenie:

```java
try {
    queue.take();
} catch (InterruptedException exception) {
    Thread.currentThread().interrupt();
    return;
}
```

Dzięki temu wyższa warstwa nadal może rozpoznać, że praca została anulowana. Jeżeli sygnatura metody na to pozwala, często lepiej przekazać `InterruptedException` wyżej zamiast podejmować lokalną decyzję.

## `Future.cancel(true)`

W przypadku zadania uruchomionego przez `ExecutorService` wywołanie:

```java
future.cancel(true);
```

próbuje przerwać wątek wykonujący zadanie. Parametr `true` nie oznacza „zatrzymaj na pewno”. Zadanie nadal musi współpracować z mechanizmem interruption.

Samo anulowanie `Future` oznacza również, że wynik nie będzie już dostępny przez `get()`. Nie jest to dowód, że kod zadania faktycznie zakończył wykonywanie.

## Sprzątanie zasobów

Zadanie powinno opuszczać pracę przez kontrolowaną ścieżkę i zwalniać zasoby w `finally` albo przez `try-with-resources`:

```java
try (Resource resource = openResource()) {
    while (!Thread.currentThread().isInterrupted()) {
        processNextItem(resource);
    }
}
```

Nie należy używać przestarzałego `Thread.stop()`. Może ono przerwać kod w środku sekcji krytycznej i pozostawić współdzielony stan w niespójnej postaci.

## Typowe błędy

- pusty `catch (InterruptedException ignored)`,
- długa pętla obliczeniowa bez punktów sprawdzania flagi,
- uznanie `Future.isCancelled()` za dowód zakończenia zadania,
- wywołanie `shutdownNow()` i założenie, że wszystkie zadania już się zatrzymały,
- czyszczenie flagi przez `Thread.interrupted()` bez świadomego powodu,
- logowanie przerwania i kontynuowanie tej samej pracy.

## Jak uruchomić test

Z katalogu `backend-engineering`:

```shell
mvn --batch-mode --no-transfer-progress -Dtest=CancelTest test
```

Test wadliwego zadania używa wątku daemon. Jest to wyłącznie zabezpieczenie testu: celowo błędne zadanie nie oferuje poprawnej drogi zakończenia i nie może blokować zamknięcia JVM testowej.

## Kryteria ukończenia

Po przejściu przykładu powinieneś umieć:

- wyjaśnić, dlaczego interruption jest mechanizmem kooperacyjnym,
- wskazać różnicę między `isInterrupted()` i `interrupted()`,
- poprawnie obsłużyć `InterruptedException`,
- wyjaśnić ograniczenia `Future.cancel(true)` i `shutdownNow()`,
- zaprojektować test anulowania, który nie pozostawia wiszących wątków.
