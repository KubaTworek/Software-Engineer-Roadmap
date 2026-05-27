Deadlock w systemach współbieżnych
==================================

Wprowadzenie
------------

Deadlock (zakleszczenie) jest jednym z najpoważniejszych problemów poprawności w systemach wielowątkowych. Występuje w sytuacji, gdy kilka wątków pozostaje w stanie permanentnego oczekiwania, ponieważ każdy z nich czeka na zasób, który jest aktualnie przetrzymywany przez inny wątek z tej samej grupy. W rezultacie żaden z nich nie jest w stanie kontynuować pracy.

W przeciwieństwie do wielu innych błędów współbieżności, deadlock nie generuje wyjątku ani błędu wykonania. Aplikacja może nadal działać, inne wątki mogą wykonywać się poprawnie, jednak część logiki systemu zostaje całkowicie zablokowana. Z tego powodu deadlocki są szczególnie trudne do diagnozowania -- często pojawiają się dopiero w środowisku produkcyjnym, przy określonych przeplotach wykonania wątków i specyficznych warunkach obciążenia.

Z perspektywy projektowania systemów współbieżnych deadlock należy traktować jako problem architektoniczny. Oznacza to, że najskuteczniejszą metodą radzenia sobie z nim jest zapobieganie jego powstawaniu już na etapie projektowania modelu współbieżności, a nie próba jego wykrywania i naprawiania w czasie działania systemu.

* * * * *

Warunki powstania deadlocka
---------------------------

Aby doszło do deadlocka, muszą zostać spełnione cztery klasyczne warunki opisane przez Coffmana. Jeśli choć jeden z nich zostanie wyeliminowany, zakleszczenie nie może wystąpić.

Pierwszym warunkiem jest **mutual exclusion**, czyli wyłączność zasobu. Oznacza to, że przynajmniej jeden zasób w systemie musi być dostępny tylko dla jednego wątku w danym momencie. W praktyce są to różnego rodzaju blokady, takie jak monitory (`synchronized`), obiekty `Lock`, czy inne mechanizmy synchronizacji.

Drugim warunkiem jest **hold and wait**. Wątek, który już posiada jeden zasób, próbuje jednocześnie uzyskać dostęp do kolejnego, nie zwalniając wcześniej tego, który już posiada. Powoduje to sytuację, w której zasoby są częściowo zajęte przez różne wątki.

Trzecim warunkiem jest **no preemption**, czyli brak możliwości odebrania zasobu wątkowi w sposób wymuszony. Zasób może zostać zwolniony tylko dobrowolnie przez wątek, który go posiada.

Ostatnim warunkiem jest **circular wait**. W systemie powstaje cykl zależności pomiędzy wątkami, w którym każdy wątek czeka na zasób przetrzymywany przez inny wątek z tego samego cyklu. W takiej sytuacji żaden z wątków nie może kontynuować pracy.

* * * * *

Deadlock jako problem grafowy
-----------------------------

Deadlock można analizować w kategoriach grafu zależności pomiędzy wątkami i zasobami. W tak zwanym **wait-for graph** węzły reprezentują wątki, a krawędzie oznaczają zależność od zasobu posiadanego przez inny wątek.

Jeżeli w takim grafie pojawi się cykl, oznacza to, że każdy wątek w cyklu oczekuje na inny wątek z tej samej grupy. W praktyce prowadzi to do sytuacji, w której żaden z nich nie jest w stanie zwolnić zasobu potrzebnego pozostałym.

Z tej perspektywy deadlock jest w istocie **problemem powstawania cykli w grafie zależności zasobów**. Właśnie dlatego większość strategii zapobiegania deadlockom polega na takim zaprojektowaniu systemu, aby powstanie cyklu było niemożliwe.

* * * * *

Globalna kolejność zasobów
--------------------------

Jedną z najbardziej klasycznych i skutecznych metod zapobiegania deadlockom jest wprowadzenie **globalnego porządku zdobywania zasobów**. W tym podejściu wszystkim zasobom przypisywana jest jednoznaczna kolejność, a wątki są zobowiązane zdobywać blokady wyłącznie w tej kolejności.

Jeżeli wszystkie wątki przestrzegają tej samej reguły, niemożliwe staje się utworzenie cyklu w grafie zależności. Każdy wątek zawsze zdobywa zasoby w tym samym kierunku porządku, co eliminuje możliwość wzajemnego oczekiwania.

Strategia ta jest szczególnie popularna w systemach, w których operacje wymagają jednoczesnego dostępu do wielu zasobów. W takich przypadkach wprowadzenie jednoznacznego porządku znacząco upraszcza analizę poprawności i pozwala uniknąć wielu trudnych do wykrycia błędów współbieżności.

Jednocześnie metoda ta wymaga konsekwentnego stosowania w całym systemie. Jeżeli choć jedno miejsce w kodzie złamie ustaloną regułę kolejności, ryzyko deadlocka ponownie się pojawia.

* * * * *

Timed locking i mechanizmy retry
--------------------------------

Inną strategią jest zastosowanie blokad z limitem czasu oczekiwania. Zamiast blokować wątek na czas nieokreślony podczas próby zdobycia zasobu, można spróbować uzyskać blokadę z określonym timeoutem. Jeśli w tym czasie nie uda się zdobyć wszystkich wymaganych zasobów, wątek zwalnia te, które już zdobył, i ponawia próbę operacji.

Takie podejście eliminuje warunek **hold and wait**, ponieważ wątek nie przetrzymuje zasobu w nieskończoność, oczekując na kolejny. Zamiast tego system dynamicznie wycofuje się z nieudanej próby i ponawia operację.

Metoda ta zwiększa elastyczność systemu, zwłaszcza w środowiskach o dużej dynamice i wysokiej konkurencji o zasoby. Jednocześnie wprowadza pewne koszty -- operacje mogą być powtarzane wielokrotnie, co zwiększa zużycie CPU i może prowadzić do zjawiska zwanego **livelockiem**, w którym wątki nie są zablokowane, ale mimo to nie osiągają postępu.

* * * * *

Thread confinement
------------------

Innym podejściem jest całkowita zmiana modelu współbieżności poprzez ograniczenie modyfikacji danych do jednego wątku. Zamiast wielu wątków współdzielących stan i synchronizujących dostęp do niego, wszystkie operacje mutujące wykonywane są przez jeden wątek roboczy.

Pozostałe wątki komunikują się z nim poprzez kolejkę zadań lub mechanizm przekazywania wiadomości. Dzięki temu dostęp do danych staje się deterministyczny i nie wymaga stosowania blokad.

Model ten jest podstawą wielu nowoczesnych architektur współbieżnych, takich jak **actor model** czy **event loop**. W praktyce oznacza to rezygnację z bezpośredniego współdzielenia stanu na rzecz komunikacji poprzez wiadomości lub zadania.

Choć podejście to eliminuje ryzyko deadlocków i znacząco upraszcza rozumowanie o współbieżności, ma również swoje ograniczenia. Najważniejszym z nich jest ograniczona równoległość -- jeden wątek wykonuje wszystkie operacje mutujące, co może stać się wąskim gardłem systemu.

* * * * *

Rola Java Memory Model
----------------------

Projektując systemy współbieżne w Javie należy również uwzględnić zasady **Java Memory Model**, które definiują sposób widoczności i uporządkowania operacji pomiędzy wątkami.

Mechanizmy synchronizacji, takie jak `synchronized`, `ReentrantLock` czy operacje wykonywane przez `ExecutorService`, wprowadzają relacje **happens-before**, które gwarantują poprawną widoczność zmian stanu pomiędzy wątkami. Oznacza to, że zapis wykonany przez jeden wątek przed zwolnieniem blokady będzie widoczny dla innego wątku po zdobyciu tej samej blokady.

Bez tych gwarancji system mógłby obserwować nieaktualne wartości zmiennych lub niepoprawną kolejność operacji, co prowadziłoby do błędów trudnych do wykrycia.

* * * * *

Projektowanie systemów odpornych na deadlock
--------------------------------------------

W praktyce eliminowanie deadlocków nie sprowadza się do zastosowania jednego mechanizmu. W dobrze zaprojektowanych systemach stosuje się zestaw zasad projektowych, które razem minimalizują ryzyko zakleszczeń.

Do najważniejszych z nich należy ograniczanie współdzielonego stanu, preferowanie obiektów niemutowalnych, stosowanie spójnej kolejności zdobywania blokad oraz unikanie zagnieżdżonych blokad, jeśli nie są absolutnie konieczne. W wielu przypadkach korzystniejsze okazuje się również zastosowanie architektury opartej na komunikacji między komponentami zamiast bezpośredniego współdzielenia danych.

W nowoczesnych systemach backendowych coraz częściej stosuje się modele oparte na komunikacji asynchronicznej, przetwarzaniu zdarzeń oraz message passing. Takie podejście zmniejsza liczbę miejsc w systemie, w których konieczna jest synchronizacja, a tym samym znacząco redukuje ryzyko deadlocków.

* * * * *

Podsumowanie
------------

Deadlock jest fundamentalnym problemem systemów współbieżnych, który wynika z niewłaściwego zarządzania dostępem do zasobów. Jego diagnozowanie bywa trudne, ponieważ często ujawnia się dopiero w specyficznych warunkach wykonania.

Najskuteczniejszym sposobem radzenia sobie z deadlockami jest zapobieganie ich powstawaniu poprzez odpowiednie projektowanie modelu współbieżności. Obejmuje to zarówno stosowanie ustalonych zasad zdobywania zasobów, jak i wybór architektury ograniczającej współdzielenie stanu.

W praktyce systemy o wysokiej niezawodności minimalizują liczbę miejsc wymagających synchronizacji, preferują komunikację pomiędzy komponentami zamiast współdzielenia danych oraz stosują mechanizmy kontrolujące sposób zdobywania zasobów. Dzięki temu ryzyko powstania deadlocka zostaje znacząco ograniczone już na poziomie architektury systemu.