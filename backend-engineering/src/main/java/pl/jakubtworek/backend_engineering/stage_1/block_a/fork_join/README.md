Fork/Join Framework w Javie -- Notatka techniczna
================================================

Fork/Join Framework jest jednym z kluczowych mechanizmów współbieżności w Javie, zaprojektowanym z myślą o efektywnym wykonywaniu obliczeń równoległych na wielu rdzeniach procesora. Został wprowadzony w Java 7 jako część pakietu `java.util.concurrent`. Jego głównym celem jest maksymalne wykorzystanie dostępnych zasobów CPU poprzez dynamiczne dzielenie pracy na mniejsze zadania oraz ich równoległe wykonywanie przez pulę wątków roboczych.

Centralnym elementem tego mechanizmu jest klasa `ForkJoinPool`, która zarządza zestawem wyspecjalizowanych wątków roboczych. W przeciwieństwie do klasycznych pul wątków, takich jak `ThreadPoolExecutor`, ForkJoinPool został zaprojektowany specjalnie pod kątem dużej liczby niewielkich zadań, które mogą być dynamicznie dzielone na jeszcze mniejsze fragmenty pracy. Dzięki temu mechanizmowi system jest w stanie efektywnie rozkładać obciążenie między wszystkie dostępne rdzenie procesora.

Model działania -- Divide and Conquer
------------------------------------

Fork/Join opiera się na klasycznym modelu algorytmicznym **divide and conquer**. Złożony problem jest dzielony na mniejsze podproblemy, które mogą być przetwarzane równolegle. Każde z tych zadań może zostać ponownie podzielone na jeszcze mniejsze fragmenty, aż do momentu osiągnięcia rozmiaru, który można efektywnie przetworzyć sekwencyjnie.

Proces wykonywania zadania można opisać trzema etapami:

Najpierw zadanie jest **dzielone (fork)** na mniejsze podzadania. Następnie część pracy wykonywana jest bezpośrednio w bieżącym wątku (**compute**), podczas gdy inne fragmenty pracy trafiają do kolejki zadań w puli wątków. Na końcu następuje etap **join**, w którym wyniki podzadań są zbierane i łączone w końcowy rezultat.

W praktyce oznacza to, że zamiast tworzyć wiele niezależnych wątków, program operuje na lekkich zadaniach, które są zarządzane przez wyspecjalizowaną pulę wątków.

Work-Stealing -- kluczowy mechanizm równoważenia obciążenia
--------------------------------------------------

Jedną z najważniejszych cech ForkJoinPool jest zastosowanie algorytmu **work-stealing**. Każdy wątek roboczy posiada własną kolejkę zadań, z której pobiera zadania do wykonania. Nowe zadania są zazwyczaj dodawane na szczyt kolejki i wykonywane przez ten sam wątek, który je utworzył.

Jeżeli dany wątek zakończy wykonywanie swoich zadań i jego kolejka stanie się pusta, może on „ukraść" zadanie z kolejki innego wątku. Operacja ta odbywa się z przeciwnej strony kolejki, co znacząco ogranicza potrzebę synchronizacji między wątkami.

Dzięki temu mechanizmowi system automatycznie równoważy obciążenie między wątkami roboczymi. Jeżeli część wątków kończy pracę szybciej, mogą one przejąć zadania od bardziej obciążonych wątków. Pozwala to na efektywne wykorzystanie wszystkich rdzeni procesora nawet przy nierównomiernym rozkładzie pracy.

Typy zadań w Fork/Join
----------------------

Podstawową abstrakcją w tym modelu jest klasa `ForkJoinTask`, która reprezentuje jednostkę pracy wykonywaną przez ForkJoinPool. W praktyce najczęściej korzysta się z dwóch jej specjalizacji.

Pierwszą z nich jest `RecursiveTask`, która reprezentuje zadanie zwracające wynik. Jest to najczęstszy wybór w przypadku algorytmów obliczeniowych, gdzie każde podzadanie produkuje częściowy rezultat, który następnie jest scalany z wynikami innych zadań.

Drugą opcją jest `RecursiveAction`, która reprezentuje zadanie nie zwracające wartości. Tego typu zadania są używane w sytuacjach, gdy wynik operacji jest zapisywany bezpośrednio w strukturach danych lub gdy operacja polega wyłącznie na wykonaniu pewnych efektów ubocznych.

Granularność zadań
------------------

Jednym z najważniejszych aspektów projektowania algorytmów w modelu Fork/Join jest właściwy dobór **granularności zadań**. Zbyt duże zadania ograniczają potencjał równoległości, ponieważ tylko niewielka liczba wątków może wykonywać pracę w danym momencie. Z drugiej strony zbyt małe zadania generują duży narzut związany z ich tworzeniem i planowaniem.

Dlatego w praktyce stosuje się mechanizm progu, po przekroczeniu którego zadanie przestaje być dzielone i jest wykonywane sekwencyjnie. Dzięki temu można ograniczyć liczbę tworzonych zadań i jednocześnie zachować wysoką równoległość obliczeń.

Dobór odpowiedniej wartości progu często wymaga eksperymentów i zależy od charakteru przetwarzanych danych oraz kosztu pojedynczej operacji.

Charakterystyka zadań odpowiednich dla Fork/Join
------------------------------------------------

ForkJoinPool został zaprojektowany przede wszystkim do obsługi **zadań CPU-bound**, czyli takich, które intensywnie wykorzystują procesor, ale nie wykonują operacji blokujących. Typowe zastosowania obejmują algorytmy przetwarzania dużych zbiorów danych, operacje matematyczne, przetwarzanie tablic, operacje mapowania lub redukcji danych oraz różnego rodzaju algorytmy rekurencyjne.

Mechanizm ten jest szczególnie skuteczny w sytuacjach, gdy zadania można naturalnie podzielić na wiele niezależnych fragmentów pracy. W takich przypadkach ForkJoinPool pozwala osiągnąć bardzo dobrą skalowalność wraz ze wzrostem liczby rdzeni procesora.

Zadania blokujące i ich wpływ na pulę
-------------------------------------

ForkJoinPool zakłada, że wykonywane zadania są krótkie i nie blokują wątków roboczych. Jeżeli jednak zadanie wykonuje operacje takie jak oczekiwanie na I/O, długotrwałe blokady synchronizacyjne lub inne operacje powodujące zatrzymanie wątku, może to znacząco obniżyć wydajność całego systemu.

W takiej sytuacji wątek roboczy pozostaje zablokowany, podczas gdy inne zadania czekają w kolejce na wykonanie. Jeżeli liczba dostępnych wątków jest niewielka, może to prowadzić do sytuacji, w której system przestaje efektywnie wykonywać pracę, mimo że w kolejce znajdują się zadania gotowe do uruchomienia.

Z tego powodu w projektowaniu aplikacji przyjmuje się zasadę, że ForkJoinPool powinien być używany głównie do operacji obliczeniowych, natomiast zadania wykonujące operacje blokujące powinny korzystać z innych mechanizmów współbieżności.

Managed Blocking
----------------

W sytuacjach, gdy operacji blokującej nie da się uniknąć, ForkJoinPool udostępnia mechanizm **managed blocking**. Pozwala on poinformować pulę wątków, że dany worker może zostać zablokowany. Dzięki temu pula może tymczasowo zwiększyć liczbę aktywnych wątków roboczych i utrzymać odpowiedni poziom równoległości.

Mechanizm ten jest realizowany poprzez interfejs `ForkJoinPool.ManagedBlocker`, który pozwala kontrolować moment rozpoczęcia oraz zakończenia operacji blokującej. Dzięki temu pula wątków może odpowiednio reagować na zmiany w stanie wykonywania zadań.

Zastosowanie w ekosystemie Javy
-------------------------------

ForkJoinPool nie jest wykorzystywany wyłącznie bezpośrednio przez programistów. Stanowi on również fundament wielu wyższych abstrakcji w ekosystemie Javy. Jest między innymi używany przez **parallel streams**, które automatycznie rozdzielają operacje na elementach kolekcji między wiele wątków. Wykorzystują go również mechanizmy takie jak `CompletableFuture`, które w wielu przypadkach wykonują asynchroniczne operacje właśnie w wspólnym ForkJoinPool.

Domyślną pulą jest `ForkJoinPool.commonPool()`, która jest współdzielona przez różne komponenty systemu. W niektórych przypadkach korzystne może być jednak utworzenie własnej instancji ForkJoinPool, aby mieć większą kontrolę nad poziomem równoległości i charakterem wykonywanych zadań.

Podsumowanie
------------

Fork/Join Framework jest zaawansowanym narzędziem do budowania wysokowydajnych aplikacji równoległych w Javie. Dzięki zastosowaniu lekkich zadań, algorytmu work-stealing oraz dynamicznego podziału pracy umożliwia efektywne wykorzystanie wielordzeniowych procesorów.

Mechanizm ten najlepiej sprawdza się w przypadku obliczeń intensywnie wykorzystujących CPU i możliwych do podziału na wiele niezależnych fragmentów pracy. Kluczowe znaczenie dla jego wydajności ma odpowiedni dobór granularności zadań oraz unikanie operacji blokujących w wątkach roboczych.

Przy właściwym zastosowaniu Fork/Join może znacząco poprawić skalowalność i wydajność aplikacji przetwarzających duże ilości danych lub wykonujących złożone obliczenia równoległe.