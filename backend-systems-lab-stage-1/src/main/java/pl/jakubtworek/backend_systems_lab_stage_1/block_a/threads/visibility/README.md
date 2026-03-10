Współdzielenie stanu i widoczność pamięci w programach wielowątkowych (Java)
==================================================

Programowanie współbieżne w Javie wymaga świadomego zarządzania dostępem do współdzielonego stanu. W przeciwieństwie do programów jednowątkowych, w których kolejność wykonania instrukcji jest deterministyczna z perspektywy jednego przepływu sterowania, w środowisku wielowątkowym wiele wątków może jednocześnie odczytywać i modyfikować te same dane. Bez odpowiednich mechanizmów synchronizacji prowadzi to do trudnych do wykrycia błędów takich jak niespójny stan obiektu, utrata aktualizacji lub obserwowanie nieaktualnych wartości.

Podstawą zrozumienia tych problemów jest **Java Memory Model (JMM)**, który definiuje zasady widoczności pamięci oraz dopuszczalne optymalizacje wykonywane przez kompilator i procesor.

Model pamięci i lokalne kopie danych
------------------------------------

Java Memory Model zakłada istnienie **pamięci głównej (main memory)**, która jest współdzielona przez wszystkie wątki, oraz **pamięci roboczej wątku (working memory)**. Wątki mogą przechowywać w niej lokalne kopie zmiennych, które wcześniej zostały odczytane z pamięci głównej. Takie kopie mogą znajdować się w rejestrach procesora lub w cache CPU.

W praktyce oznacza to, że wątek nie musi za każdym razem odczytywać wartości z pamięci głównej. Może pracować na wcześniej pobranej kopii. Jeżeli inny wątek zmodyfikuje tę zmienną, pierwszy wątek nie musi natychmiast zobaczyć tej zmiany. Bez odpowiednich mechanizmów synchronizacji aktualizacja może pozostać niewidoczna przez nieokreślony czas.

To zjawisko określa się jako **problem widoczności (visibility problem)** i jest ono jednym z fundamentalnych zagadnień programowania współbieżnego.

Trzy podstawowe problemy współbieżności
---------------------------------------

W kontekście współdzielonego stanu w Javie wyróżnia się trzy główne klasy problemów: widoczność, atomowość oraz kolejność wykonywania instrukcji.

### Widoczność (visibility)

Widoczność oznacza, czy zmiana dokonana przez jeden wątek jest widoczna dla innych wątków. Jeżeli mechanizmy synchronizacji nie zostaną zastosowane, wątek może operować na lokalnej kopii wartości i nigdy nie zauważyć aktualizacji wykonanej przez inny wątek.

Problem ten wynika bezpośrednio z optymalizacji stosowanych w nowoczesnych procesorach oraz przez kompilator JIT.

### Atomowość (atomicity)

Operacja jest atomowa, jeśli jest wykonywana jako pojedyncza niepodzielna jednostka. Wiele operacji, które na poziomie kodu wydają się proste, w rzeczywistości składa się z kilku kroków. Na przykład inkrementacja zmiennej liczbowej obejmuje odczyt wartości, jej modyfikację oraz zapis wyniku.

Jeżeli między tymi krokami inny wątek uzyska dostęp do tej samej zmiennej, może dojść do utraty aktualizacji lub powstania niespójnego stanu.

### Kolejność wykonywania instrukcji (ordering)

Kompilator oraz procesor mogą zmieniać kolejność wykonywania instrukcji w celu optymalizacji działania programu. Takie **przeorganizowanie instrukcji (reordering)** jest dopuszczalne, o ile nie zmienia semantyki programu w pojedynczym wątku. Jednak w kontekście wielu wątków może to prowadzić do obserwowania operacji w innej kolejności niż wynika to z kodu źródłowego.

Dlatego model pamięci musi definiować formalne zasady określające, kiedy operacje wykonane w jednym wątku stają się widoczne dla innych.

Relacja happens-before
----------------------

Centralnym pojęciem Java Memory Model jest relacja **happens-before**. Jeżeli jedna operacja happens-before drugiej, oznacza to, że wszystkie efekty pierwszej operacji są widoczne dla drugiej oraz że ich kolejność jest gwarantowana.

Relacja ta nie wynika wyłącznie z kolejności instrukcji w kodzie. Powstaje dopiero poprzez zastosowanie odpowiednich mechanizmów synchronizacji. Java definiuje kilka sytuacji, w których taka relacja powstaje, między innymi przy użyciu synchronizacji monitorów (`synchronized`), zmiennych oznaczonych jako `volatile`, uruchamiania nowych wątków czy oczekiwania na zakończenie wątku.

Dzięki tej relacji możliwe jest formalne określenie, kiedy zmiany dokonane przez jeden wątek muszą być widoczne dla innych.

Synchronizacja z użyciem `synchronized`
---------------------------------------

Jednym z podstawowych mechanizmów synchronizacji w Javie jest słowo kluczowe `synchronized`. Mechanizm ten opiera się na monitorach powiązanych z obiektami. Gdy wątek wchodzi do sekcji oznaczonej jako `synchronized`, musi najpierw uzyskać dostęp do odpowiedniego monitora. Dopóki monitor jest zajęty przez inny wątek, kolejne wątki są blokowane.

Synchronizacja za pomocą monitorów zapewnia trzy istotne właściwości. Po pierwsze, gwarantuje **wzajemne wykluczanie (mutual exclusion)**, co oznacza, że tylko jeden wątek może wykonywać sekcję krytyczną w danym momencie. Po drugie, zapewnia **widoczność zmian w pamięci**, ponieważ opuszczenie sekcji synchronizowanej powoduje zapis zmian do pamięci głównej. Po trzecie, wprowadza **gwarancje dotyczące kolejności wykonywania operacji**, zapobiegając niepożądanemu przeorganizowaniu instrukcji przez kompilator lub procesor.

Dzięki tym właściwościom `synchronized` jest bardzo silnym i bezpiecznym mechanizmem synchronizacji. Jego wadą może być jednak potencjalny koszt związany z blokowaniem wątków oraz ograniczona skalowalność przy dużej liczbie konkurujących wątków.

Zmienna `volatile`
------------------

Alternatywnym mechanizmem jest oznaczenie zmiennej jako `volatile`. Zmienna taka posiada specjalną semantykę pamięciową. Zapis do zmiennej `volatile` jest natychmiast publikowany w pamięci głównej, a każdy odczyt takiej zmiennej musi pobrać aktualną wartość z pamięci, zamiast korzystać z lokalnej kopii.

`volatile` zapewnia więc **widoczność zmian** oraz **ograniczenie przeorganizowania instrukcji** wokół operacji odczytu i zapisu tej zmiennej. Nie zapewnia jednak wzajemnego wykluczania ani atomowości operacji złożonych. Oznacza to, że nadaje się przede wszystkim do prostych przypadków, takich jak flagi stanu czy sygnały zakończenia pracy.

W sytuacjach wymagających modyfikacji wielu pól lub utrzymania złożonych invariantów konieczne jest zastosowanie pełnej synchronizacji.

Bezpieczna publikacja obiektów
------------------------------

Ważnym aspektem współdzielenia danych między wątkami jest **bezpieczna publikacja obiektów (safe publication)**. Jeżeli obiekt zostanie udostępniony innym wątkom bez odpowiedniej synchronizacji, istnieje ryzyko, że wątek odczytujący zobaczy obiekt w stanie częściowo skonstruowanym. Może to oznaczać, że niektóre pola będą posiadały wartości domyślne lub nieaktualne.

Poprawna publikacja obiektu może zostać osiągnięta poprzez mechanizmy takie jak synchronizacja monitorów, pola `volatile`, inicjalizacja statyczna klas lub wykorzystanie struktur z pakietu `java.util.concurrent`.

Busy waiting i pętle aktywnego oczekiwania
------------------------------------------

W niektórych przypadkach stosuje się konstrukcje polegające na ciągłym sprawdzaniu warunku w pętli, bez wykonywania dodatkowych operacji. Tego typu podejście nazywa się **busy waiting** lub **spin waiting**. Wątek aktywnie zużywa wtedy czas procesora, oczekując na zmianę stanu.

Rozwiązanie to może mieć sens w systemach wymagających bardzo niskiej latencji, jednak w większości aplikacji biznesowych prowadzi do niepotrzebnego zużycia zasobów. Dlatego często korzysta się z mechanizmów blokujących, które pozwalają wątkowi przejść w stan oczekiwania bez aktywnego wykorzystywania CPU.

Narzędzia z `java.util.concurrent`
----------------------------------

Współczesne aplikacje rzadko polegają wyłącznie na niskopoziomowych mechanizmach synchronizacji. Pakiet `java.util.concurrent` dostarcza bogatego zestawu narzędzi zaprojektowanych specjalnie do budowania systemów współbieżnych.

Znajdują się tam między innymi zmienne atomowe umożliwiające wykonywanie operacji bez blokowania, zaawansowane mechanizmy blokad, synchronizatory służące do koordynacji wielu wątków oraz kolekcje zaprojektowane z myślą o bezpiecznym dostępie współbieżnym.

Zastosowanie tych narzędzi pozwala uniknąć wielu błędów związanych z ręcznym zarządzaniem synchronizacją i jednocześnie poprawia skalowalność systemu.

Podsumowanie
------------

Programowanie wielowątkowe w Javie wymaga zrozumienia zasad działania Java Memory Model oraz konsekwencji współdzielenia danych między wątkami. Kluczowe problemy obejmują widoczność zmian w pamięci, atomowość operacji oraz kolejność wykonywania instrukcji.

Mechanizmy takie jak `synchronized`, `volatile` oraz narzędzia dostępne w `java.util.concurrent` umożliwiają kontrolowanie tych aspektów i budowanie poprawnych systemów współbieżnych. W praktyce właściwy dobór mechanizmu zależy od charakteru współdzielonego stanu oraz wymagań dotyczących wydajności i skalowalności.