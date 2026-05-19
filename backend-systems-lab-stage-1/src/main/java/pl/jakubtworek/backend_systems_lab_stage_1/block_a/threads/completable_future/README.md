CompletableFuture w Javie -- notatka techniczna
==============================================

Wprowadzenie
------------

Współczesne aplikacje backendowe bardzo często wykonują wiele operacji równolegle: pobierają dane z różnych serwisów, komunikują się z bazami danych, wywołują zewnętrzne API lub przetwarzają dane w wielu etapach. W takich sytuacjach kluczowe jest unikanie blokowania wątków i efektywne zarządzanie współbieżnością.

Klasa `CompletableFuture` z pakietu `java.util.concurrent` stanowi jedno z podstawowych narzędzi do budowania asynchronicznego przepływu operacji w Javie. Rozszerza ona koncepcję klasy `Future`, która reprezentuje wynik operacji dostępny w przyszłości, dodając możliwość budowania złożonych łańcuchów przetwarzania, łączenia wielu operacji oraz obsługi błędów w sposób deklaratywny.

`CompletableFuture` pozwala traktować operacje asynchroniczne jako elementy pipeline'u przetwarzania danych. Zamiast blokować wątek i czekać na wynik operacji, można zdefiniować kolejne kroki, które zostaną wykonane automatycznie po zakończeniu wcześniejszego etapu.

* * * * *

Podstawowa koncepcja
====================

`CompletableFuture` reprezentuje wynik operacji, która może zakończyć się w przyszłości. Obiekt tej klasy przechowuje stan operacji oraz pozwala zarejestrować funkcje, które zostaną wykonane po jej zakończeniu.

Operacja reprezentowana przez `CompletableFuture` może zakończyć się na trzy sposoby. Może zostać zakończona normalnie i zwrócić wynik, może zakończyć się wyjątkiem, albo zostać zakończona ręcznie przez kod programu. Dzięki temu `CompletableFuture` może być wykorzystywany zarówno jako narzędzie do zarządzania zadaniami asynchronicznymi, jak i jako mechanizm synchronizacji pomiędzy różnymi komponentami systemu.

Istotną cechą tej klasy jest możliwość budowania łańcuchów operacji. Kolejne etapy przetwarzania mogą być deklarowane jako transformacje wyników poprzednich etapów, co pozwala budować czytelne pipeline'y przetwarzania danych.

* * * * *

Tworzenie operacji asynchronicznych
===================================

Najczęściej zadania asynchroniczne są tworzone przy użyciu metod fabrycznych takich jak `supplyAsync` lub `runAsync`.

Metoda `supplyAsync` służy do uruchamiania operacji, które zwracają wynik. Z kolei `runAsync` jest wykorzystywana w sytuacjach, gdy interesuje nas jedynie wykonanie zadania bez wartości zwrotnej.

Domyślnie operacje uruchamiane przez `CompletableFuture` są wykonywane w puli wątków `ForkJoinPool.commonPool`. Jest to współdzielona pula wątków dostępna dla całej aplikacji. W prostych programach takie podejście jest wystarczające, jednak w systemach produkcyjnych często stosuje się dedykowane pule wątków przekazywane poprzez `Executor`. Pozwala to lepiej kontrolować liczbę równolegle wykonywanych zadań, separować różne typy pracy oraz unikać przeciążenia globalnej puli wątków.

* * * * *

Kompozycja operacji
===================

Jedną z największych zalet `CompletableFuture` jest możliwość komponowania operacji w formie pipeline'u. Zamiast wykonywać zadania w sposób imperatywny, można deklarować kolejne etapy przetwarzania jako transformacje wyników poprzednich operacji.

Najprostszym mechanizmem transformacji jest metoda `thenApply`. Pozwala ona przekształcić wynik wcześniejszego etapu i przekazać go do kolejnego kroku przetwarzania. Mechanizm ten działa podobnie do operacji `map` znanej z API strumieni.

W sytuacji, gdy funkcja transformująca zwraca kolejny `CompletableFuture`, używa się metody `thenCompose`. Metoda ta spłaszcza strukturę zagnieżdżonych futures i pozwala zachować liniowy przepływ operacji. Bez takiego mechanizmu łatwo byłoby doprowadzić do powstania zagnieżdżonych struktur typu `CompletableFuture<CompletableFuture<T>>`, które są trudne w użyciu.

Często spotykanym przypadkiem jest również przetwarzanie wyniku bez potrzeby zwracania kolejnej wartości. W takich sytuacjach stosuje się metodę `thenAccept`, która pozwala wykonać operację kończącą pipeline, np. zapis do logów, wysłanie zdarzenia lub aktualizację stanu systemu.

* * * * *

Łączenie wielu operacji
=======================

W wielu systemach konieczne jest wykonywanie kilku niezależnych operacji równolegle i agregowanie ich wyników. `CompletableFuture` udostępnia kilka mechanizmów umożliwiających takie scenariusze.

Metoda `thenCombine` pozwala połączyć wyniki dwóch niezależnych operacji asynchronicznych. Funkcja łącząca zostanie wykonana dopiero wtedy, gdy oba futures zakończą się sukcesem. Dzięki temu można w prosty sposób agregować dane pochodzące z różnych źródeł.

Jeżeli liczba operacji jest większa, często stosuje się metodę `allOf`. Pozwala ona utworzyć nowy `CompletableFuture`, który zakończy się dopiero wtedy, gdy wszystkie przekazane futures zostaną zakończone. Mechanizm ten jest często wykorzystywany w scenariuszach równoległego pobierania danych lub przetwarzania wielu zadań jednocześnie.

Istnieje również metoda `anyOf`, która kończy się w momencie zakończenia pierwszego z przekazanych futures. Jest ona przydatna w scenariuszach typu race condition, gdzie liczy się najszybsza odpowiedź, na przykład przy zapytaniach do wielu regionów infrastruktury.

* * * * *

Obsługa błędów
==============

Operacje asynchroniczne mogą zakończyć się błędami, dlatego ważnym elementem pracy z `CompletableFuture` jest odpowiednia obsługa wyjątków.

Jednym z podstawowych mechanizmów jest metoda `exceptionally`, która pozwala przechwycić wyjątek i zwrócić wartość zastępczą. Dzięki temu pipeline przetwarzania nie zostaje przerwany, a system może zastosować strategię fallback.

Bardziej ogólnym mechanizmem jest metoda `handle`, która otrzymuje zarówno wynik operacji, jak i ewentualny wyjątek. Pozwala to w jednym miejscu obsłużyć oba scenariusze i zdecydować o dalszym przebiegu przetwarzania.

Metoda `whenComplete` jest natomiast przeznaczona głównie do wykonywania operacji ubocznych, takich jak logowanie. W przeciwieństwie do innych metod nie zmienia ona wyniku przyszłej operacji.

* * * * *

Timeouty i kontrola czasu wykonania
===================================

W systemach rozproszonych ważnym aspektem jest kontrolowanie czasu wykonywania operacji, szczególnie gdy komunikujemy się z zewnętrznymi usługami.

`CompletableFuture` oferuje mechanizmy pozwalające ograniczyć maksymalny czas oczekiwania na wynik. Metoda `completeOnTimeout` umożliwia zakończenie operacji wartością domyślną w przypadku przekroczenia określonego limitu czasu. Alternatywnie można użyć metody `orTimeout`, która spowoduje zakończenie operacji wyjątkiem `TimeoutException`.

Mechanizmy te pozwalają budować bardziej odporne systemy, które potrafią reagować na opóźnienia w komunikacji z zewnętrznymi komponentami.

* * * * *

Pobieranie wyników
==================

Mimo że `CompletableFuture` jest narzędziem do pracy asynchronicznej, w pewnym momencie często konieczne jest uzyskanie wyniku operacji.

Najczęściej używana jest metoda `join`, która blokuje aktualny wątek do momentu zakończenia operacji i zwraca wynik. W przeciwieństwie do metody `get`, nie wymaga obsługi checked exceptions, ponieważ ewentualne błędy są opakowane w `CompletionException`.

Metoda `get` pochodzi z wcześniejszego API `Future` i wymaga obsługi wyjątków takich jak `InterruptedException` oraz `ExecutionException`.

* * * * *

Strategie wykonania
===================

Metody transformujące w `CompletableFuture` występują w dwóch wariantach: synchronicznym oraz asynchronicznym.

Warianty synchroniczne, takie jak `thenApply`, wykonują się w wątku, który zakończył poprzedni etap pipeline'u. Warianty asynchroniczne, takie jak `thenApplyAsync`, uruchamiają kolejny etap w puli wątków.

Możliwość wyboru pomiędzy tymi strategiami pozwala lepiej kontrolować model wykonania oraz unikać przeciążenia pojedynczych wątków.

* * * * *

Manualne kończenie operacji
===========================

`CompletableFuture` może zostać również zakończony ręcznie. Pozwala to integrować go z systemami opartymi na callbackach, zdarzeniach lub starszym kodzie asynchronicznym.

Operację można zakończyć normalnie przy użyciu metody `complete`, albo zakończyć ją wyjątkowo za pomocą `completeExceptionally`. Mechanizm ten jest często wykorzystywany przy adaptacji zewnętrznych bibliotek lub integracji z systemami event-driven.

* * * * *

Najlepsze praktyki
==================

Podczas pracy z `CompletableFuture` warto pamiętać o kilku zasadach projektowych. Przede wszystkim należy unikać blokowania wątków w środku pipeline'u, ponieważ niweluje to korzyści wynikające z asynchronicznego modelu wykonania.

Istotne jest również stosowanie dedykowanych pul wątków dla różnych typów operacji, szczególnie w systemach intensywnie korzystających z operacji IO. Dzięki temu można uniknąć sytuacji, w której długotrwałe operacje blokują wątki potrzebne do innych zadań.

Kolejną dobrą praktyką jest świadome projektowanie obsługi błędów. Brak obsługi wyjątków w pipeline'ach asynchronicznych może prowadzić do trudnych do wykrycia problemów oraz przerwanych przepływów przetwarzania.

* * * * *

Podsumowanie
============

`CompletableFuture` stanowi jedno z najważniejszych narzędzi do implementacji współbieżności w nowoczesnych aplikacjach Java. Pozwala budować czytelne i elastyczne pipeline'y operacji asynchronicznych, łączyć wiele niezależnych zadań oraz zarządzać błędami i czasem wykonania.

Odpowiednio używany umożliwia tworzenie systemów, które lepiej wykorzystują zasoby sprzętowe, skalują się efektywniej i unikają nadmiernego blokowania wątków. Jednocześnie wymaga świadomego projektowania przepływu zadań oraz odpowiedniego zarządzania pulami wątków, aby zachować czytelność i przewidywalność działania systemu.