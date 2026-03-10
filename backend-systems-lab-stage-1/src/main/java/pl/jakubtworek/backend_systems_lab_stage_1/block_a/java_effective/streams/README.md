Java Streams and Functional Processing --- Advanced Notes
==================================================

Wprowadzenie
------------

Współczesne aplikacje Java coraz częściej operują na dużych zbiorach danych, które wymagają transformacji, filtrowania oraz agregacji. Choć Java Collections Framework dostarcza podstawowych struktur danych do przechowywania elementów, to prawdziwa siła nowoczesnego modelu przetwarzania danych w Javie pojawiła się wraz z wprowadzeniem **Stream API w Java 8**.

Stream API zmieniło sposób pracy z kolekcjami, wprowadzając bardziej deklaratywny i funkcyjny styl programowania. Zamiast ręcznej iteracji po elementach kolekcji, programista może opisać **pipeline transformacji danych**, w którym kolejne operacje są stosowane do strumienia elementów.

W tym modelu kolekcje przestają być centralnym elementem logiki programu. Stają się jedynie **źródłem danych**, które mogą być przetwarzane przy użyciu pipeline'u operacji.

Zrozumienie tego podejścia wymaga jednak nie tylko znajomości samego API, ale również zrozumienia mechanizmów takich jak lazy evaluation, reduction, spliterators, parallel processing oraz optymalizacje JVM.

* * * * *

Streams as Data Processing Pipelines
====================================

Stream w Javie nie jest strukturą danych. Jest abstrakcją reprezentującą **sekwencję elementów, na których można wykonywać operacje przetwarzania**.

Stream działa w modelu pipeline, który składa się z trzech części:

-   źródła danych (source)
-   operacji pośrednich (intermediate operations)
-   operacji terminalnej (terminal operation)

Źródłem danych najczęściej jest kolekcja, ale może nim być również tablica, generator danych lub dowolny obiekt udostępniający mechanizm iteracji.

Operacje pośrednie tworzą kolejne etapy transformacji danych, natomiast operacja terminalna uruchamia faktyczne przetwarzanie elementów.

Ważną cechą tego modelu jest to, że **stream nie przechowuje danych**. Elementy są pobierane ze źródła, przetwarzane przez pipeline i konsumowane przez operację terminalną.

* * * * *

Lazy Evaluation
===============

Jedną z najważniejszych cech Stream API jest **lazy evaluation**.

Operacje pośrednie nie wykonują się w momencie ich zdefiniowania. Zamiast tego tworzą one opis pipeline'u, który zostaje wykonany dopiero w momencie wywołania operacji terminalnej.

Oznacza to, że pipeline nie działa etapami na całej kolekcji. Zamiast tego elementy są przetwarzane **pojedynczo przez cały pipeline**.

Model przetwarzania wygląda w uproszczeniu następująco:

element → operacja 1 → operacja 2 → operacja 3 → wynik

dopiero po przetworzeniu jednego elementu pobierany jest kolejny.

Takie podejście pozwala ograniczyć zużycie pamięci oraz umożliwia zastosowanie operacji short-circuit, które mogą zakończyć przetwarzanie zanim wszystkie elementy zostaną przetworzone.

* * * * *

Intermediate Operations
=======================

Operacje pośrednie transformują stream i zwracają nowy stream. Nie wykonują one jednak żadnych obliczeń dopóki nie pojawi się operacja terminalna.

Najczęściej stosowane operacje to filtrowanie elementów, transformacje wartości oraz zmiana struktury danych.

Ważną cechą operacji pośrednich jest to, że są one **stateless**. Oznacza to, że operacja nie powinna zależeć od stanu zewnętrznego ani od wcześniej przetworzonych elementów.

Dzięki temu pipeline może być bezpiecznie optymalizowany oraz potencjalnie wykonywany równolegle.

* * * * *

Terminal Operations
===================

Operacje terminalne kończą pipeline i powodują rozpoczęcie przetwarzania danych. Po wykonaniu operacji terminalnej stream nie może zostać użyty ponownie.

Operacje terminalne mogą zwracać pojedynczą wartość, strukturę danych lub wykonywać akcję uboczną.

Ważną kategorią operacji terminalnych są operacje short-circuit, które mogą zakończyć przetwarzanie wcześniej, gdy tylko zostanie znaleziony wynik spełniający warunek.

* * * * *

Reduction
=========

Jednym z najczęstszych zastosowań streamów jest agregacja danych, czyli redukcja wielu elementów do jednej wartości wynikowej.

Redukcja może polegać na obliczeniu sumy, znalezieniu maksimum lub zbudowaniu struktury wynikowej.

Stream API oferuje dwa główne mechanizmy redukcji: `reduce` oraz `collect`.

Metoda `reduce` jest operacją czysto funkcyjną, w której elementy są łączone przy użyciu funkcji akumulującej. Każdy krok redukcji zwraca nową wartość wynikową.

Metoda `collect` wykorzystuje natomiast **mutable reduction**, w której elementy są dodawane do struktury wynikowej.

To podejście jest bardziej efektywne w przypadku budowania kolekcji wynikowych, ponieważ nie wymaga tworzenia nowych obiektów przy każdej iteracji.

* * * * *

Collectors
==========

Collector opisuje sposób zbierania elementów streama do struktury wynikowej. Definiuje on cztery kluczowe elementy procesu redukcji:

-   sposób utworzenia kontenera wynikowego
-   sposób dodawania elementów do kontenera
-   sposób łączenia częściowych wyników
-   sposób przekształcenia wyniku końcowego

Mechanizm collectorów jest szczególnie ważny w kontekście parallel streams, gdzie dane mogą być przetwarzane w wielu wątkach i muszą zostać połączone w jeden wynik.

* * * * *

Stateless vs Stateful Operations
================================

Operacje w streamach mogą być stateless lub stateful.

Operacje stateless nie zależą od poprzednich elementów streama. Każdy element jest przetwarzany niezależnie.

Operacje stateful wymagają natomiast wiedzy o całym zbiorze danych. Przykładem jest sortowanie lub usuwanie duplikatów. Operacje tego typu często wymagają buforowania elementów i mogą mieć większy koszt pamięciowy.

* * * * *

Spliterator
===========

Pod maską Stream API wykorzystuje mechanizm `Spliterator`. Jest on rozszerzeniem klasycznego iteratora i oprócz iteracji umożliwia również dzielenie danych na fragmenty.

Spliterator jest kluczowy dla działania parallel streams. Pozwala on dzielić dane na mniejsze części, które mogą być przetwarzane niezależnie przez różne wątki.

Każdy fragment danych jest następnie przetwarzany przez pipeline operacji, a wyniki są łączone w końcowy rezultat.

* * * * *

Parallel Streams
================

Stream API umożliwia równoległe przetwarzanie danych przy użyciu parallel streams. W tym modelu dane są dzielone na fragmenty i przetwarzane przez wiele wątków jednocześnie.

Implementacja opiera się na `ForkJoinPool`, który wykorzystuje strategię work stealing. Jeśli jeden wątek zakończy swoje zadanie, może przejąć pracę z kolejki innego wątku.

Choć parallel streams mogą znacząco przyspieszyć operacje obliczeniowe, nie zawsze są najlepszym rozwiązaniem. Koszt podziału danych oraz synchronizacji wątków może przewyższyć zysk z równoległości, szczególnie w przypadku małych zbiorów danych.

* * * * *

Side Effects
============

Stream API zostało zaprojektowane w oparciu o zasady programowania funkcyjnego. Jedną z kluczowych zasad jest unikanie efektów ubocznych.

Operacje w pipeline powinny być stateless i nie powinny modyfikować zewnętrznego stanu programu. Wprowadzanie efektów ubocznych utrudnia reasoning o kodzie oraz może prowadzić do problemów z równoległością.

* * * * *

Performance Characteristics of Streams
======================================

Choć streamy oferują bardzo czytelny i deklaratywny sposób przetwarzania danych, wprowadzają również pewien narzut wydajnościowy.

Pipeline stream składa się z wielu warstw abstrakcji, takich jak lambdy, obiekty pipeline oraz mechanizm iteracji przez Spliterator. W prostych operacjach klasyczna pętla może być szybsza, ponieważ generuje mniej wywołań metod i nie wymaga dodatkowych obiektów.

Jednak w wielu przypadkach JVM jest w stanie zoptymalizować pipeline przy użyciu mechanizmów takich jak method inlining, escape analysis oraz loop fusion.

* * * * *

JVM Optimizations
=================

HotSpot JVM stosuje szereg optymalizacji, które mogą znacząco zmniejszyć koszt wykonania streamów.

Jedną z najważniejszych jest **method inlining**, w którym wywołanie lambdy zostaje zastąpione bezpośrednim kodem operacji.

Kolejną istotną optymalizacją jest **escape analysis**, która pozwala JVM wykryć obiekty tymczasowe i usunąć ich alokację.

W przypadku primitive streams możliwe jest również wykorzystanie **vectorization**, czyli przetwarzania wielu wartości jednocześnie przy użyciu instrukcji SIMD.

* * * * *

Kiedy Stream API nie jest najlepszym wyborem
============================================

Choć streamy są bardzo wygodne, nie zawsze są optymalnym rozwiązaniem. W szczególności należy uważać na ich używanie w krytycznych ścieżkach wydajnościowych lub w bardzo ciasnych pętlach.

Również bardzo długie pipeline'y mogą pogorszyć czytelność kodu i utrudnić jego debugowanie.

* * * * *

Podsumowanie
============

Stream API wprowadziło w Javie model przetwarzania danych oparty na pipeline'ach transformacji, który znacząco upraszcza pracę z kolekcjami i pozwala pisać bardziej deklaratywny kod.

Aby jednak korzystać z tego modelu efektywnie, konieczne jest zrozumienie nie tylko samego API, ale również mechanizmów stojących za jego implementacją. Należą do nich lazy evaluation, reduction, spliterators, parallel processing oraz optymalizacje JVM.

Dopiero zrozumienie tych mechanizmów pozwala świadomie decydować kiedy streamy są najlepszym narzędziem, a kiedy prostsze rozwiązania mogą być bardziej efektywne.