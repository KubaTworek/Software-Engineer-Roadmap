Java Generics --- notatka techniczna
==================================

Wprowadzenie
------------

Generics są jednym z kluczowych elementów systemu typów w języku Java. Ich głównym celem jest umożliwienie tworzenia klas, interfejsów i metod, które mogą operować na różnych typach danych w sposób bezpieczny typowo i jednocześnie zachować ogólność implementacji.

Najważniejszą zaletą generics jest zapewnienie **bezpieczeństwa typów na etapie kompilacji**. Dzięki temu wiele błędów, które wcześniej pojawiały się dopiero w czasie działania programu, może zostać wykrytych już podczas kompilacji. Dodatkowo generics eliminują potrzebę ręcznego rzutowania typów, co poprawia czytelność i utrzymanie kodu.

Generics są szeroko wykorzystywane w wielu elementach platformy Java, między innymi w kolekcjach, bibliotekach narzędziowych, frameworkach backendowych oraz API strumieni.

* * * * *

Parametry typów
---------------

Podstawowym mechanizmem generics są **parametry typów**. Parametr typu reprezentuje konkretny typ danych, który zostanie określony dopiero w momencie użycia klasy, interfejsu lub metody.

Parametry typów pozwalają tworzyć komponenty, które są jednocześnie:

-   ogólne
-   wielokrotnego użytku
-   bezpieczne typowo

W praktyce oznacza to, że jedna implementacja może działać z wieloma typami danych bez potrzeby duplikowania kodu.

W konwencjach Javy przyjęło się stosowanie krótkich symboli jako nazw parametrów typów. Najczęściej spotykane oznaczenia to:

-   `T` --- ogólny typ (Type)
-   `E` --- element struktury danych (Element)
-   `K` --- klucz (Key)
-   `V` --- wartość (Value)
-   `N` --- typ liczbowy (Number)

Nazwy te nie mają znaczenia dla kompilatora, ale poprawiają czytelność kodu.

* * * * *

Generics w klasach, interfejsach i metodach
-------------------------------------------

Generics mogą być stosowane w trzech podstawowych miejscach:

-   klasach
-   interfejsach
-   metodach

Parametry typów w klasach i interfejsach definiują ogólną strukturę danych lub komponentu, natomiast parametry typów w metodach pozwalają definiować algorytmy działające na różnych typach.

Metody generyczne są szczególnie przydatne w bibliotekach narzędziowych, gdzie jedna metoda może działać na wielu różnych typach bez konieczności tworzenia wielu przeciążonych wersji.

* * * * *

Inwariancja typów generycznych
------------------------------

Istotną cechą generics w Javie jest **inwariancja typów parametryzowanych**. Oznacza to, że dwa typy generyczne nie są kompatybilne nawet wtedy, gdy ich parametry typów pozostają w relacji dziedziczenia.

Na przykład fakt, że jeden typ jest podtypem drugiego, nie oznacza automatycznie, że struktury generyczne oparte na tych typach również są kompatybilne. Taki mechanizm zapobiega sytuacjom, w których struktura danych mogłaby zostać naruszona przez operacje wprowadzające elementy niezgodne z jej rzeczywistym typem.

Inwariancja jest więc istotnym elementem systemu bezpieczeństwa typów w Javie.

* * * * *

Wildcards
---------

Aby umożliwić bardziej elastyczne operowanie na typach generycznych, Java wprowadza mechanizm **wildcardów**. Wildcard pozwala określić, że typ parametru jest częściowo nieznany lub należy do określonej hierarchii.

Wildcard z górnym ograniczeniem (`extends`) oznacza, że parametr typu musi być danym typem lub jego podtypem. Jest on często wykorzystywany w sytuacjach, gdy struktura danych jest źródłem wartości.

Wildcard z dolnym ograniczeniem (`super`) oznacza natomiast, że parametr typu musi być danym typem lub jego nadtypem. Takie ograniczenie jest przydatne w sytuacjach, gdy struktura danych przyjmuje nowe wartości.

W projektowaniu API często stosuje się zasadę znaną jako **PECS (Producer Extends, Consumer Super)**. Zasada ta pomaga określić, kiedy należy używać górnych, a kiedy dolnych ograniczeń wildcardów.

* * * * *

Ograniczone parametry typów
---------------------------

Parametry typów mogą być ograniczone do określonej klasy lub interfejsu. Takie ograniczenie pozwala kompilatorowi zagwarantować, że dany typ posiada określone właściwości lub metody.

Dzięki temu kod generyczny może bezpiecznie korzystać z metod dostępnych w klasie bazowej lub interfejsie, do którego ograniczony jest parametr typu.

Możliwe jest również stosowanie wielu ograniczeń jednocześnie. W takim przypadku typ musi spełniać wszystkie zadeklarowane wymagania.

* * * * *

Type Erasure
------------

Implementacja generics w Javie opiera się na mechanizmie **type erasure**. Oznacza to, że informacje o parametrach typów są wykorzystywane przez kompilator podczas analizy kodu, ale nie są przechowywane w pełnej formie w czasie działania programu.

Podczas kompilacji parametry typów są usuwane, a kod zostaje przekształcony tak, aby operował na typach bazowych. Mechanizm ten pozwolił wprowadzić generics bez naruszenia kompatybilności wstecznej z wcześniejszymi wersjami języka.

Type erasure wprowadza jednak pewne ograniczenia. W runtime nie ma możliwości sprawdzenia konkretnego parametru typu. Nie można także tworzyć instancji parametrów typów ani bezpośrednio tworzyć tablic typów generycznych.

* * * * *

Reifiable i non-reifiable types
-------------------------------

W kontekście type erasure istotne jest rozróżnienie między typami reifiable i non-reifiable.

Typ reifiable to taki, którego pełna informacja o typie jest dostępna w runtime. Dotyczy to między innymi klas, typów prymitywnych oraz tablic.

Typy generyczne z parametrami są natomiast typami non-reifiable, ponieważ informacja o ich parametrach typów nie jest dostępna w runtime.

Z tego powodu niektóre operacje, takie jak tworzenie tablic typów generycznych, nie są możliwe w Javie.

* * * * *

Heap Pollution
--------------

Heap pollution to sytuacja, w której zmienna zadeklarowana jako typ generyczny wskazuje na obiekt innego typu niż deklarowany. Zjawisko to pojawia się najczęściej wtedy, gdy generics są mieszane z mechanizmami, które nie są w pełni typowane.

Typowymi przyczynami heap pollution są:

-   używanie raw types
-   mieszanie generics i tablic
-   stosowanie varargs z typami generycznymi

Problem ten jest szczególnie niebezpieczny, ponieważ kod może kompilować się poprawnie, a błąd pojawi się dopiero w runtime.

* * * * *

Bridge Methods
--------------

W wyniku działania type erasure kompilator czasami generuje dodatkowe metody pomocnicze zwane **bridge methods**. Ich zadaniem jest zachowanie poprawnego polimorfizmu w sytuacjach, gdy metoda generyczna nadpisuje metodę z klasy bazowej.

Bridge methods nie są widoczne w kodzie źródłowym, ale można je zauważyć w bytecode lub podczas analizy klas przy użyciu refleksji.

* * * * *

Generics a refleksja
--------------------

Ze względu na type erasure dostęp do informacji o typach generycznych w runtime jest ograniczony. W niektórych przypadkach możliwe jest jednak uzyskanie informacji o parametrach typów poprzez analizę metadanych klasy.

Java udostępnia w tym celu mechanizmy refleksji, takie jak `ParameterizedType`, które pozwalają odczytać parametry typów zapisane w deklaracjach pól, klas lub metod.

* * * * *

Dobre praktyki
--------------

Podczas projektowania kodu wykorzystującego generics należy dążyć do konsekwentnego stosowania parametrów typów oraz unikania raw types, które omijają mechanizm bezpieczeństwa typów.

Wildcards powinny być stosowane wtedy, gdy zwiększają elastyczność API, jednak ich nadmierne użycie może utrudniać czytelność kodu oraz inferencję typów przez kompilator.

Dobrze zaprojektowany kod generyczny powinien być jednocześnie:

-   bezpieczny typowo
-   czytelny
-   elastyczny
-   łatwy do ponownego wykorzystania.

* * * * *

Podsumowanie
------------

Generics są jednym z najważniejszych mechanizmów systemu typów w Javie. Pozwalają tworzyć ogólne i bezpieczne typowo komponenty, które mogą być wykorzystywane w różnych kontekstach bez konieczności powielania kodu.

Zrozumienie mechanizmów takich jak inwariancja, wildcardy, bounded types czy type erasure jest kluczowe przy projektowaniu bibliotek, frameworków oraz dużych systemów backendowych, w których poprawne zarządzanie typami ma bezpośredni wpływ na stabilność i czytelność kodu.

* * * * *

Jeśli chcesz, mogę jeszcze zrobić **wersję tej notatki naprawdę na poziomie senior interview (taką ~2x bardziej zaawansowaną)**, gdzie pojawią się jeszcze:

-   **variance vs site variance vs use-site variance**
-   **type inference w generics**
-   **capture conversion**
-   **generic API design patterns**
-   **dlaczego Java nie ma reified generics (vs Kotlin / C#)**

To są rzeczy, które bardzo często pojawiają się u **Senior Java / Staff Java**.

mozesz rzoszerzyc

Java Generics --- rozszerzona notatka techniczna
==============================================

Wprowadzenie
------------

Generics stanowią jeden z najważniejszych elementów systemu typów w języku Java. Pozwalają tworzyć klasy, interfejsy i metody, które operują na różnych typach danych przy zachowaniu bezpieczeństwa typów oraz wysokiej reużywalności kodu. Mechanizm ten został wprowadzony w Javie 5, aby rozwiązać problemy wynikające z wcześniejszego modelu pracy z typami ogólnymi, w którym wiele struktur danych operowało na typie `Object`.

W starszym modelu programista musiał ręcznie rzutować wartości podczas ich odczytu. Prowadziło to do sytuacji, w których błędy typów były wykrywane dopiero w czasie działania programu. Generics przenoszą tę weryfikację na etap kompilacji, dzięki czemu wiele potencjalnych błędów może zostać wykrytych wcześniej.

Generics są szeroko wykorzystywane w standardowej bibliotece Javy, w szczególności w strukturach danych, bibliotekach narzędziowych, API strumieni oraz wielu frameworkach backendowych. Ich prawidłowe zrozumienie jest kluczowe dla projektowania bibliotek i API, które są zarówno elastyczne, jak i bezpieczne typowo.

* * * * *

Parametry typów
===============

Podstawą generics są parametry typów, które pozwalają opisać strukturę danych lub algorytm w sposób niezależny od konkretnego typu. Parametr typu reprezentuje typ, który zostanie określony dopiero w momencie użycia klasy lub metody.

Dzięki temu jedna implementacja może obsługiwać wiele typów danych bez potrzeby tworzenia osobnych wersji kodu.

W konwencjach Javy przyjęto stosowanie krótkich symboli jako nazw parametrów typów. Najczęściej spotykane oznaczenia to:

-   `T` -- ogólny typ (Type)
-   `E` -- element (Element)
-   `K` -- klucz (Key)
-   `V` -- wartość (Value)
-   `N` -- typ liczbowy (Number)

Nazwy te są jedynie konwencją stylistyczną, ale znacząco poprawiają czytelność kodu.

* * * * *

Generics w klasach, interfejsach i metodach
===========================================

Generics mogą być stosowane w trzech głównych kontekstach:

1.  klasach
2.  interfejsach
3.  metodach

Parametry typów w klasach i interfejsach definiują ogólną strukturę komponentu. Natomiast parametry typów w metodach umożliwiają tworzenie algorytmów działających na różnych typach bez zależności od konkretnej implementacji klasy.

Metody generyczne są szczególnie przydatne w bibliotekach narzędziowych oraz w operacjach na strukturach danych, gdzie jeden algorytm powinien działać dla wielu typów.

* * * * *

Inwariancja typów generycznych
==============================

Typy generyczne w Javie są **invariantne**. Oznacza to, że nawet jeśli jeden typ jest podtypem drugiego, struktury generyczne oparte na tych typach nie są ze sobą kompatybilne.

Na przykład relacja dziedziczenia pomiędzy dwoma klasami nie oznacza automatycznie kompatybilności pomiędzy ich odpowiednikami generycznymi. Takie zachowanie zapobiega sytuacjom, w których możliwe byłoby wprowadzenie do struktury danych elementów niezgodnych z jej rzeczywistym typem.

Inwariancja jest zatem ważnym elementem mechanizmu bezpieczeństwa typów.

* * * * *

Variance w generics
===================

Variance opisuje sposób, w jaki relacje dziedziczenia pomiędzy typami wpływają na relacje pomiędzy typami generycznymi.

W języku Java variance jest obsługiwana poprzez **wildcards** i jest stosowana na poziomie użycia typu (use-site variance).

Istnieją trzy podstawowe formy variance:

### Covariance

Covariance pozwala traktować strukturę generyczną jako źródło danych. Oznacza to, że można odczytywać wartości jako typ bazowy, ale nie można bezpiecznie dodawać nowych elementów.

### Contravariance

Contravariance pozwala traktować strukturę generyczną jako konsumenta danych. W takim przypadku możliwe jest dodawanie elementów określonego typu, natomiast odczyt wartości jest ograniczony.

### Invariance

Domyślnie typy generyczne w Javie są invariantne, co oznacza brak kompatybilności pomiędzy różnymi parametrami typów.

* * * * *

Wildcards
=========

Wildcard pozwala określić parametr typu, który jest częściowo nieznany. Dzięki temu możliwe jest operowanie na strukturach danych o różnych typach parametrów.

Wildcard z górnym ograniczeniem (`extends`) oznacza, że typ musi być określonym typem lub jego podtypem. Takie ograniczenie jest stosowane w sytuacjach, gdy struktura danych jest traktowana jako źródło wartości.

Wildcard z dolnym ograniczeniem (`super`) oznacza, że typ musi być określonym typem lub jego nadtypem. Jest to używane wtedy, gdy struktura danych przyjmuje nowe wartości.

W projektowaniu API często stosuje się zasadę **PECS (Producer Extends Consumer Super)**, która pomaga określić właściwe ograniczenia typów w zależności od roli struktury danych.

* * * * *

Bounded Type Parameters
=======================

Parametry typów mogą być ograniczone do określonej klasy lub interfejsu. Takie ograniczenie zapewnia, że parametr typu posiada określone właściwości lub metody.

Dzięki temu kod generyczny może bezpiecznie korzystać z funkcjonalności udostępnianej przez klasę bazową lub interfejs.

Możliwe jest również stosowanie wielu ograniczeń jednocześnie. W takim przypadku typ musi spełniać wszystkie zadeklarowane wymagania.

* * * * *

Type Inference
==============

Kompilator Javy potrafi w wielu sytuacjach automatycznie wnioskować typy parametrów generycznych na podstawie kontekstu użycia. Mechanizm ten nazywany jest **type inference**.

Dzięki niemu programista nie zawsze musi jawnie podawać parametry typów. Kompilator analizuje typy argumentów oraz oczekiwany typ zwracany i na tej podstawie określa odpowiedni parametr typu.

Type inference znacząco poprawia czytelność kodu i redukuje ilość powtarzalnych deklaracji typów.

* * * * *

Capture Conversion
==================

Wildcardy mogą prowadzić do sytuacji, w której kompilator nie potrafi jednoznacznie określić konkretnego typu parametru. W takich przypadkach stosowany jest mechanizm **capture conversion**.

Capture conversion polega na tym, że kompilator tworzy wewnętrzny typ reprezentujący nieznany parametr typu. Dzięki temu możliwe jest wykonywanie operacji na strukturach zawierających wildcardy.

Mechanizm ten jest całkowicie transparentny dla programisty, ale odgrywa istotną rolę w systemie typów generics.

* * * * *

Type Erasure
============

Implementacja generics w Javie opiera się na mechanizmie **type erasure**. Oznacza to, że parametry typów są używane przez kompilator podczas analizy kodu, ale nie są przechowywane w runtime.

Podczas kompilacji parametry typów są usuwane, a kod zostaje przekształcony tak, aby operował na typach bazowych.

Mechanizm ten został zastosowany głównie w celu zachowania kompatybilności wstecznej z kodem napisanym przed wprowadzeniem generics.

* * * * *

Reifiable i non-reifiable types
===============================

W kontekście type erasure typy można podzielić na:

### Reifiable types

Typy, których pełna informacja o typie jest dostępna w runtime.

### Non-reifiable types

Typy, które tracą informacje o swoich parametrach typów po kompilacji.

Typy generyczne z parametrami należą do drugiej kategorii.

* * * * *

Heap Pollution
==============

Heap pollution to sytuacja, w której zmienna zadeklarowana jako typ generyczny wskazuje na obiekt innego typu niż deklarowany.

Zjawisko to pojawia się najczęściej w wyniku mieszania generics z mechanizmami, które nie są w pełni typowane, takimi jak raw types, tablice lub varargs.

Efektem heap pollution jest zazwyczaj wyjątek `ClassCastException` pojawiający się w runtime.

* * * * *

Bridge Methods
==============

W wyniku działania type erasure kompilator czasami generuje dodatkowe metody pomocnicze zwane **bridge methods**.

Ich zadaniem jest zachowanie poprawnego polimorfizmu w sytuacjach, gdy metoda generyczna nadpisuje metodę z klasy bazowej.

Bridge methods są automatycznie generowane przez kompilator i nie są widoczne w kodzie źródłowym.

* * * * *

Generics a Reflection
=====================

Mechanizm type erasure powoduje, że dostęp do informacji o parametrach typów w runtime jest ograniczony. Jednak w niektórych przypadkach możliwe jest odczytanie parametrów typów poprzez analizę metadanych klas.

Java udostępnia w tym celu elementy API refleksji takie jak `ParameterizedType`, które pozwalają analizować parametry typów zapisane w deklaracjach klas, metod lub pól.

* * * * *

Dlaczego Java nie posiada reified generics
==========================================

W niektórych językach programowania, takich jak C# czy Kotlin, generics są implementowane w sposób **reified**, co oznacza, że informacje o parametrach typów są dostępne w runtime.

Java zdecydowała się na implementację opartą o type erasure głównie ze względu na kompatybilność wsteczną z wcześniejszymi wersjami platformy.

Decyzja ta pozwoliła uniknąć zmian w maszynie wirtualnej oraz istniejących bibliotekach, ale jednocześnie wprowadziła pewne ograniczenia w pracy z generics.

* * * * *

Projektowanie API z użyciem generics
====================================

Projektowanie API wykorzystującego generics wymaga szczególnej uwagi. Należy dążyć do tego, aby typy były jasno określone, a interfejs był intuicyjny dla użytkownika.

Dobre API generyczne powinno:

-   jasno określać parametry typów
-   minimalizować konieczność rzutowania
-   stosować wildcardy tylko wtedy, gdy zwiększają elastyczność
-   unikać raw types

Dobrze zaprojektowany kod generyczny jest bardziej reużywalny, bezpieczny typowo i łatwiejszy w utrzymaniu.

* * * * *

Podsumowanie
============

Generics stanowią jeden z najważniejszych mechanizmów systemu typów w Javie i są kluczowe przy projektowaniu bibliotek, struktur danych oraz frameworków.

Zrozumienie takich mechanizmów jak variance, wildcardy, type erasure, type inference czy bounded types pozwala tworzyć kod, który jest jednocześnie elastyczny, bezpieczny i skalowalny.

W dużych systemach backendowych poprawne wykorzystanie generics znacząco wpływa na jakość architektury oraz stabilność aplikacji.