Java Collections Framework -- Notatki do rozmów technicznych
==================================================

Wprowadzenie
------------

Java Collections Framework (JCF) to zestaw interfejsów i implementacji struktur danych służących do przechowywania oraz manipulowania grupami obiektów. Framework powstał, aby ujednolicić sposób pracy z kolekcjami w Javie oraz zapewnić zestaw wydajnych implementacji najczęściej używanych struktur danych.

Jednym z najważniejszych założeń projektowych frameworka jest oddzielenie **interfejsu od implementacji**. Oznacza to, że kod aplikacji powinien operować na interfejsach takich jak `List`, `Set`, `Map` czy `Queue`, a konkretna implementacja powinna być szczegółem technicznym.

Przykładowo zamiast deklarować zmienną jako konkretną klasę implementacji, stosuje się interfejs:

    List<User> users = new ArrayList<>();

Takie podejście pozwala łatwo zmienić implementację kolekcji w przyszłości bez konieczności modyfikowania kodu biznesowego.

* * * * *

Główna struktura Java Collections Framework
===========================================

Java Collections Framework opiera się na kilku głównych interfejsach.

Najważniejszym z nich jest `Collection`, który stanowi bazę dla większości struktur danych. Z niego wywodzą się trzy podstawowe typy kolekcji:

-   **List** -- kolekcja uporządkowana
-   **Set** -- zbiór unikalnych elementów
-   **Queue** -- struktura przetwarzania elementów w określonej kolejności

Interfejs `Map` stanowi osobną hierarchię i nie dziedziczy po `Collection`, ponieważ reprezentuje inną koncepcję struktury danych -- przechowywanie par klucz-wartość.

* * * * *

List -- kolekcja uporządkowana
=============================

Interfejs `List` reprezentuje kolekcję, w której elementy są przechowywane w określonej kolejności. Lista dopuszcza duplikaty i pozwala na dostęp do elementów poprzez indeks.

Najczęściej spotykane implementacje to struktury oparte na tablicach dynamicznych oraz listy powiązane.

W przypadku implementacji tablicowej elementy są przechowywane w ciągłym obszarze pamięci. Dzięki temu dostęp do elementu na podstawie indeksu ma stałą złożoność czasową. Jest to jedna z głównych zalet tej struktury.

Jednakże wstawianie elementów w środku listy może być kosztowne, ponieważ wymaga przesunięcia części elementów w tablicy.

W implementacjach opartych o listy powiązane elementy przechowywane są w strukturze węzłów zawierających referencje do kolejnych elementów. Pozwala to na szybkie operacje wstawiania i usuwania elementów na końcach struktury, jednak dostęp do elementów przez indeks wymaga przejścia przez kolejne węzły.

W praktyce implementacje oparte na tablicach są znacznie częściej używane ze względu na:

-   lepszą lokalność pamięci
-   mniejszy narzut pamięci
-   szybsze iterowanie

* * * * *

Lokalność pamięci i wydajność
=============================

Jednym z kluczowych aspektów wydajności struktur danych jest sposób, w jaki dane są rozmieszczone w pamięci.

Procesory korzystają z wielopoziomowej pamięci cache. Jeżeli dane znajdują się blisko siebie w pamięci, procesor może je pobierać w jednym odczycie cache.

Struktury oparte na tablicach przechowują elementy w ciągłym obszarze pamięci, co powoduje bardzo dobrą lokalność przestrzenną. W przypadku struktur opartych na wskaźnikach elementy mogą znajdować się w zupełnie różnych miejscach pamięci, co prowadzi do zjawiska nazywanego **pointer chasing**.

W rezultacie iterowanie po strukturze tablicowej jest często znacząco szybsze niż po liście powiązanej, mimo że z punktu widzenia teoretycznej złożoności operacje mogą wydawać się podobne.

* * * * *

Map -- struktura klucz-wartość
=============================

Mapy przechowują dane w postaci par klucz-wartość. Każdy klucz w mapie jest unikalny, natomiast wartości mogą się powtarzać.

Najczęściej wykorzystywaną implementacją mapy jest struktura oparta na funkcji hashującej.

W tego typu strukturze dane są przechowywane w tablicy kubełków. Każdy klucz jest przekształcany przez funkcję hashującą do wartości liczbowej, która następnie określa indeks w tablicy.

Dzięki temu operacje wyszukiwania, wstawiania i usuwania mogą być wykonywane w czasie stałym w średnim przypadku.

* * * * *

Kolizje w strukturach hashujących
=================================

Kolizja występuje wtedy, gdy dwa różne klucze zostaną przekształcone przez funkcję hashującą do tego samego indeksu.

W takich sytuacjach elementy trafiają do tego samego kubełka.

Historycznie kolizje były obsługiwane przez listy powiązane przechowujące wszystkie elementy danego kubełka. W nowszych wersjach Javy wprowadzono optymalizację polegającą na konwersji długich list kolizji do struktur drzewiastych.

Gdy liczba elementów w kubełku przekroczy określony próg, lista zostaje przekształcona w samobalansujące drzewo binarne. Dzięki temu złożoność operacji wyszukiwania w najgorszym przypadku zmniejsza się z liniowej do logarytmicznej.

* * * * *

Tree-based collections
======================

Niektóre implementacje kolekcji wykorzystują drzewa samobalansujące, aby przechowywać elementy w posortowanej kolejności.

Najczęściej stosowaną strukturą jest **red-black tree**.

Drzewo czerwono-czarne to zbalansowane drzewo binarne, które gwarantuje, że długość najdłuższej ścieżki od korzenia do liścia jest ograniczona. Dzięki temu operacje wstawiania, usuwania i wyszukiwania mają złożoność logarytmiczną.

Struktury drzewiaste są używane wtedy, gdy potrzebne jest utrzymanie elementów w uporządkowanej kolejności lub wykonywanie operacji zakresowych.

* * * * *

Set -- zbiór unikalnych elementów
================================

Set jest kolekcją, która nie dopuszcza duplikatów. Wewnętrzna implementacja zbioru często wykorzystuje strukturę mapy, gdzie element zbioru pełni rolę klucza.

Podczas dodawania elementu zbiór sprawdza, czy dany element już istnieje. Jeżeli tak, operacja dodania nie zmienia stanu kolekcji.

W zależności od implementacji zbiór może:

-   nie gwarantować kolejności elementów
-   zachowywać kolejność wstawiania
-   utrzymywać elementy w kolejności sortowania

* * * * *

Kolejki i struktury przetwarzania
=================================

Kolejki służą do przetwarzania elementów według określonej strategii.

Najbardziej klasyczny model to **FIFO (First In First Out)**, gdzie element dodany jako pierwszy zostanie przetworzony jako pierwszy.

Istnieją również struktury, w których elementy są przetwarzane według priorytetu. W takich strukturach element o najwyższym priorytecie jest zwracany jako pierwszy niezależnie od kolejności dodania.

Tego typu struktury są zazwyczaj implementowane jako **kopiec binarny**.

* * * * *

Kolekcje współbieżne
====================

Standardowe kolekcje Javy nie są bezpieczne w środowisku wielowątkowym. Oznacza to, że równoczesne operacje wielu wątków mogą prowadzić do niespójności danych.

W starszych rozwiązaniach stosowano globalne blokady synchronizacji. Takie podejście jednak znacząco ograniczało skalowalność aplikacji.

Nowoczesne implementacje kolekcji współbieżnych wykorzystują bardziej zaawansowane techniki:

-   **lock striping** -- podział struktury na segmenty z osobnymi blokadami
-   **CAS (compare and swap)** -- atomowe operacje sprzętowe
-   **algorytmy bezblokadowe**

Dzięki temu możliwe jest osiągnięcie wysokiej przepustowości nawet przy dużej liczbie wątków.

* * * * *

Nowoczesne operacje Map (Java 8+)
=================================

W starszych wersjach Javy często stosowano wzorzec polegający na pobraniu wartości z mapy, jej modyfikacji i zapisaniu z powrotem.

Taki schemat był jednak podatny na problemy współbieżności.

Java 8 wprowadziła zestaw metod umożliwiających wykonanie złożonych operacji w sposób atomowy. Pozwalają one na bezpieczne aktualizowanie wartości bez konieczności ręcznego zarządzania synchronizacją.

Metody te pozwalają między innymi na:

-   obliczanie wartości tylko wtedy, gdy klucz nie istnieje
-   przeliczanie istniejących wartości
-   łączenie nowych i istniejących wartości

* * * * *

Złożoność operacji -- najważniejsze intuicje
===========================================

Podczas rozmów technicznych bardzo często oczekuje się znajomości podstawowych złożoności operacji dla różnych struktur danych.

Struktury oparte na tablicach zapewniają stały czas dostępu do elementu poprzez indeks, natomiast operacje wstawiania w środku kolekcji wymagają przesuwania elementów.

Struktury hashujące zapewniają bardzo szybkie operacje wyszukiwania w średnim przypadku, jednak mogą mieć gorszą wydajność w przypadku wielu kolizji.

Struktury drzewiaste zapewniają stabilną złożoność logarytmiczną niezależnie od rozkładu danych.

* * * * *

Wybór odpowiedniej kolekcji
===========================

Wybór struktury danych powinien zawsze wynikać z charakterystyki operacji wykonywanych w aplikacji.

Najważniejsze pytania, które warto sobie zadać:

-   czy elementy muszą być unikalne
-   czy kolejność elementów ma znaczenie
-   czy potrzebne jest sortowanie
-   jak często wykonywane są operacje wyszukiwania
-   czy kolekcja będzie używana w środowisku wielowątkowym

Świadomy wybór kolekcji może znacząco wpłynąć na wydajność oraz skalowalność systemu.

* * * * *

Podsumowanie
============

Java Collections Framework dostarcza zestaw dobrze zoptymalizowanych struktur danych, które pokrywają większość typowych przypadków użycia w aplikacjach.

Na poziomie senior ważne jest nie tylko znajomość API kolekcji, ale przede wszystkim zrozumienie:

-   wewnętrznych implementacji struktur
-   złożoności operacji
-   wpływu struktury na pamięć i cache procesora
-   zachowania kolekcji w środowisku wielowątkowym

Takie zrozumienie pozwala podejmować świadome decyzje projektowe oraz poprawnie analizować wydajność systemów.