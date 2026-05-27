# Programowanie współbieżne w Javie — ujęcie teoretyczne

## Executive Summary

Ten dokument jest teoretycznym opracowaniem najważniejszych problemów i wzorców programowania współbieżnego w Javie. Punktem ciężkości nie jest sama składnia ani katalog gotowych klas z biblioteki standardowej, lecz zrozumienie mechanizmów, które powodują błędy współbieżności: braku widoczności zmian między wątkami, naruszenia atomowości operacji, niepoprawnego publikowania obiektów, wyścigów danych, zakleszczeń oraz błędnych założeń dotyczących kolejności wykonywania instrukcji. Java daje programiście relatywnie wysokopoziomowe narzędzia, takie jak `synchronized`, `volatile`, `java.util.concurrent`, atomiki, blokady jawne i executory, ale żadne z nich nie zwalnia z rozumienia Java Memory Model.

W praktyce większość trudnych błędów współbieżności nie wynika z tego, że programista nie zna konkretnej klasy API. Wynika raczej z błędnego modelu mentalnego: założenia, że zapis wykonany przez jeden wątek „na pewno” będzie od razu widoczny dla drugiego, że pojedyncza linia kodu jest automatycznie atomowa, że kolekcja używana tylko „prawie zawsze” z jednego wątku nie wymaga ochrony, albo że test jednostkowy, który raz przechodzi, dowodzi poprawności programu współbieżnego. Współbieżność wymaga myślenia o stanie współdzielonym, niezmiennikach, granicach odpowiedzialności, własności obiektów oraz o tym, kto i kiedy może obserwować dany stan.

Najbezpieczniejsze projekty współbieżne ograniczają współdzielenie mutowalnego stanu. Jeżeli stan nie jest współdzielony, nie trzeba go synchronizować. Jeżeli obiekt jest niemutowalny, można go bezpiecznie przekazywać między wątkami. Jeżeli mutowalny stan musi być współdzielony, należy jasno określić mechanizm synchronizacji i stosować go konsekwentnie. Najgorsze rozwiązania to te, w których część dostępu do pola jest chroniona, a część odbywa się „na skróty”, ponieważ wtedy program ma pozory bezpieczeństwa, ale nie posiada realnej gwarancji poprawności.

## Java Memory Model jako fundament

Java Memory Model, w skrócie JMM, opisuje, kiedy operacje wykonywane przez jeden wątek muszą być widoczne dla innych wątków. Nie jest to wyłącznie akademicka abstrakcja. Bez JMM nie da się poprawnie odpowiedzieć na pytanie, czy odczyt pola w jednym wątku zobaczy zapis wykonany w drugim. Współczesne procesory, kompilatory JIT i mechanizmy cache mogą zmieniać kolejność wykonywania instrukcji, przechowywać wartości w rejestrach, opóźniać propagację zapisów albo optymalizować kod w sposób, który jest poprawny dla pojedynczego wątku, ale zaskakujący w programie wielowątkowym.

Najważniejsze pojęcie w JMM to relacja *happens-before*. Jeżeli operacja A happens-before operacja B, to skutki A muszą być widoczne dla B, a kolejność ta musi być respektowana z punktu widzenia modelu pamięci. Relacja ta nie oznacza wyłącznie następstwa czasowego. Dwie instrukcje mogą wykonać się w określonej kolejności fizycznie, ale jeśli nie istnieje między nimi relacja happens-before, drugi wątek nie ma gwarancji zobaczenia wyniku pierwszej instrukcji.

W Javie relacje happens-before powstają między innymi przez wejście i wyjście z tego samego monitora, czyli przez `synchronized`, przez zapis i późniejszy odczyt tego samego pola `volatile`, przez uruchomienie wątku metodą `Thread.start`, przez zakończenie wątku obserwowane przez `Thread.join`, przez zakończenie zadania przekazanego do niektórych konstrukcji z `java.util.concurrent`, a także przez mechanizmy synchronizacji takie jak `CountDownLatch`, `Future`, `BlockingQueue` czy blokady z pakietu `java.util.concurrent.locks`.

Brak relacji happens-before oznacza, że program zawiera potencjalny data race, jeżeli co najmniej jeden z dostępów jest zapisem do współdzielonego, mutowalnego stanu. Taki program może działać poprawnie przez tysiące uruchomień i zawieść dopiero na innej maszynie, pod innym obciążeniem, po aktualizacji JVM albo po pozornie niezwiązanej zmianie kodu.

## Visibility, czyli widoczność zmian

Problem widoczności polega na tym, że jeden wątek modyfikuje stan, a drugi wątek tej modyfikacji nie widzi albo widzi ją z opóźnieniem. Klasycznym przykładem jest flaga kończąca pętlę roboczą. Jeżeli pole `boolean running` nie jest oznaczone jako `volatile` i nie jest odczytywane ani zapisywane wewnątrz poprawnej sekcji krytycznej, JVM może potraktować je tak, jakby jego wartość nie zmieniała się z punktu widzenia danego wątku. Wątek może więc teoretycznie wykonywać pętlę bez końca, mimo że inny wątek ustawił flagę na `false`.

Rozwiązaniem może być użycie `volatile`, jeżeli operacja polega wyłącznie na publikowaniu prostej informacji o stanie, na przykład „zatrzymaj się” albo „konfiguracja została przeładowana”. `volatile` zapewnia widoczność i określone uporządkowanie operacji, ale nie zapewnia atomowości operacji złożonych. Dlatego `volatile int counter` nie czyni operacji `counter++` bezpieczną w wielu wątkach. Inkrementacja składa się z odczytu, obliczenia nowej wartości i zapisu. Kilka wątków może odczytać tę samą starą wartość, a następnie nadpisać nawzajem swoje wyniki.

Alternatywą dla `volatile` jest `synchronized`, które zapewnia zarówno widoczność, jak i wzajemne wykluczanie. Wyjście z bloku `synchronized` powoduje opublikowanie zmian, a wejście do bloku chronionego tym samym monitorem umożliwia ich zobaczenie. To podejście jest bezpieczniejsze przy ochronie niezmienników obejmujących więcej niż jedno pole. Jeżeli poprawność obiektu zależy od spójności kilku wartości, samo `volatile` zwykle nie wystarczy.

## Atomicity, czyli atomowość operacji

Atomowość oznacza, że operacja jest obserwowana jako niepodzielna. W kontekście współbieżności problem polega na tym, że wiele operacji wyglądających niewinnie w kodzie źródłowym wcale nie jest atomowych. Przykładowe `count++` wygląda jak jedna operacja, ale logicznie jest sekwencją: odczytaj `count`, dodaj jeden, zapisz wynik. W środowisku wielowątkowym między te kroki może wejść inny wątek.

Typowy błąd polega na tym, że programista chroni pojedyncze metody, ale nie chroni całej operacji biznesowej. Przykładowo metoda `getBalance()` i metoda `setBalance()` mogą być osobno synchronizowane, ale operacja „sprawdź saldo, a następnie wykonaj przelew” nadal może być nieatomowa, jeżeli składa się z kilku wywołań między którymi inny wątek może zmienić stan. Współbieżność wymaga identyfikowania nie pojedynczych linii kodu, lecz całych niezmienników i transakcji logicznych.

Do zapewnienia atomowości można użyć `synchronized`, `ReentrantLock`, klas atomowych takich jak `AtomicInteger`, albo modelu aktorowego / kolejkowego, w którym cały dostęp do stanu odbywa się przez jeden wątek wykonawczy. Wybór zależy od tego, czy chronimy prostą wartość, złożony niezmiennik, operację wymagającą oczekiwania, czy też chcemy całkowicie uniknąć równoległego dostępu do danego stanu.

## Safe publication

Bezpieczna publikacja oznacza przekazanie obiektu innym wątkom w taki sposób, aby zobaczyły one jego poprawnie skonstruowany stan. Problem jest subtelny, ponieważ referencja do obiektu może stać się widoczna dla innego wątku zanim konstruktor zakończy logiczną inicjalizację albo zanim zapisy wykonane w konstruktorze staną się widoczne. Szczególnie niebezpieczne jest publikowanie `this` z konstruktora, na przykład przez rejestrację listenera, uruchomienie wątku albo przekazanie referencji do globalnej kolekcji.

Najprostszą metodą bezpiecznej publikacji jest użycie obiektów niemutowalnych. Pola `final` mają specjalne właściwości w JMM: jeżeli obiekt został poprawnie skonstruowany, inne wątki powinny zobaczyć poprawne wartości pól finalnych po uzyskaniu referencji do obiektu. Nie oznacza to jednak, że każdy obiekt z polami `final` jest automatycznie niemutowalny. Jeżeli pole finalne wskazuje na mutowalną kolekcję, sama referencja jest finalna, ale zawartość kolekcji nadal może się zmieniać.

Bezpieczna publikacja może nastąpić również przez zapis do pola `volatile`, przez umieszczenie obiektu w strukturze współbieżnej, takiej jak `ConcurrentHashMap` lub `BlockingQueue`, przez inicjalizację statyczną klasy, przez przekazanie obiektu w ramach poprawnie zsynchronizowanego bloku albo przez wynik `Future`. Kluczowe jest to, że sama „widoczność referencji” nie wystarcza. Chodzi o widoczność całego stanu, który ta referencja reprezentuje.

## Thread confinement

Jedną z najskuteczniejszych strategii projektowania bezpiecznych programów współbieżnych jest ograniczenie obiektu do jednego wątku. Jeżeli dany stan jest używany wyłącznie przez jeden wątek, nie wymaga synchronizacji. Przykładem jest lokalna zmienna metody, obiekt tworzony i używany w ramach jednego zadania, albo stan obsługiwany przez pojedynczy executor.

Thread confinement bywa prostszy i bardziej niezawodny niż rozbudowana synchronizacja. Zamiast pozwalać wielu wątkom modyfikować ten sam obiekt, można wysyłać zadania do jednej kolejki, a jeden wątek wykonawczy przetwarza je sekwencyjnie. Taki model zmniejsza ryzyko data race i deadlocku, ale ma koszt: ogranicza równoległość przetwarzania tego konkretnego stanu i wymaga myślenia asynchronicznego. Jeżeli pojedynczy wątek stanie się wąskim gardłem, trzeba skalować przez partycjonowanie stanu, a nie przez przypadkowe dodawanie blokad.

## Immutability jako domyślna strategia

Effective Java mocno promuje niemutowalność, ponieważ obiekty niemutowalne są z natury bezpieczne w środowisku wielowątkowym. Jeżeli stan obiektu nie może się zmienić po konstrukcji, wiele wątków może go czytać bez synchronizacji. To upraszcza reasoning, testowanie i utrzymanie kodu. Niemutowalność dobrze współgra też z programowaniem funkcyjnym, przetwarzaniem strumieniowym oraz architekturą opartą na komunikatach.

Niemutowalność wymaga jednak dyscypliny. Klasa powinna być finalna albo skutecznie zabezpieczona przed dziedziczeniem, pola powinny być prywatne i finalne, a mutowalne komponenty powinny być kopiowane defensywnie. Szczególną uwagę trzeba zwracać na kolekcje, tablice i klasy daty/czasu starszego typu. Samo zadeklarowanie pola jako `private final List<T>` nie wystarcza, jeżeli konstruktor przechowuje przekazaną z zewnątrz listę bez kopii albo getter zwraca bezpośrednią referencję do wewnętrznej kolekcji.

Kosztem niemutowalności może być większa liczba tworzonych obiektów. W praktyce często jest to akceptowalne, a współczesna JVM dobrze radzi sobie z krótkotrwałymi obiektami. Jeżeli koszt alokacji jest realnym problemem, powinien zostać potwierdzony pomiarami, a nie założony z góry.

## synchronized

`synchronized` jest podstawowym mechanizmem synchronizacji w Javie. Zapewnia wzajemne wykluczanie oraz relację happens-before między wyjściem z bloku chronionego monitorem a późniejszym wejściem do bloku chronionego tym samym monitorem. Jego największą zaletą jest prostota. Kod z `synchronized` jest zwykle łatwiejszy do zrozumienia niż kod z ręcznie zarządzanymi blokadami, ponieważ wejście i wyjście z monitora jest powiązane ze strukturą bloku.

Najważniejszym błędem jest synchronizowanie na niewłaściwym obiekcie albo używanie różnych monitorów do ochrony tego samego stanu. Jeżeli jedno miejsce używa `synchronized(this)`, inne `synchronized(lock)`, a jeszcze inne nie synchronizuje się wcale, program nie ma spójnej polityki ochrony stanu. Dobra praktyka polega na tym, aby jasno określić, który lock chroni które pola. Często warto użyć prywatnego finalnego obiektu blokady, na przykład `private final Object lock = new Object();`, zamiast blokować się na `this`, ponieważ zewnętrzny kod nie może wtedy przypadkowo lub celowo wejść w tę samą blokadę.

`synchronized` dobrze nadaje się do ochrony małych, krótkich sekcji krytycznych oraz prostych niezmienników. Nie jest idealny, gdy potrzebujemy przerwania oczekiwania na blokadę, próby przejęcia blokady bez czekania, sprawiedliwości kolejkowania albo kilku warunków oczekiwania. W takich sytuacjach bardziej elastyczny może być `ReentrantLock`.

## ReentrantLock

`ReentrantLock` daje podobną podstawową gwarancję jak `synchronized`: jeden wątek na raz może posiadać blokadę, a poprawne użycie blokady zapewnia widoczność zmian. Różnica polega na większej kontroli. Można użyć `tryLock`, można czekać na blokadę z timeoutem, można reagować na przerwanie wątku, można utworzyć blokadę sprawiedliwą, a także można używać wielu obiektów `Condition`.

Ta elastyczność ma cenę. `ReentrantLock` wymaga ręcznego zwolnienia blokady, najczęściej w bloku `finally`. Pominięcie `unlock()` prowadzi do poważnych błędów, często trudniejszych do wykrycia niż w przypadku `synchronized`. Kod staje się też bardziej rozbudowany, a przez to bardziej podatny na pomyłki. `ReentrantLock` powinien być wybierany wtedy, gdy rzeczywiście potrzebujemy jego dodatkowych możliwości, a nie jako domyślny zamiennik `synchronized`.

W projektowaniu architektonicznym należy pamiętać, że jawne blokady nie rozwiązują same z siebie problemu złożoności współdzielonego stanu. Mogą nawet zachęcać do budowania skomplikowanych protokołów blokowania. Jeżeli poprawność wymaga przejmowania wielu blokad w różnych miejscach systemu, ryzyko deadlocku rośnie i należy rozważyć zmianę modelu, na przykład uporządkowanie kolejności blokad, ograniczenie współdzielenia stanu albo przejście na przetwarzanie przez kolejki.

## AtomicInteger i klasy atomowe

Klasy atomowe, takie jak `AtomicInteger`, `AtomicLong`, `AtomicReference` czy `AtomicBoolean`, zapewniają bezblokujące operacje atomowe oparte zwykle na instrukcjach CAS, czyli compare-and-set. Są bardzo przydatne przy prostych licznikach, flagach, referencjach konfiguracyjnych i strukturach, w których nie trzeba chronić większego niezmiennika. `AtomicInteger.incrementAndGet()` jest atomowe, więc wiele wątków może bezpiecznie zwiększać licznik bez `synchronized`.

Problem zaczyna się wtedy, gdy atomik jest używany do modelowania złożonego stanu. Jeżeli poprawność zależy od relacji między kilkoma polami, osobne atomiki nie zapewniają automatycznie spójności całej operacji. Można mieć atomowe aktualizacje poszczególnych wartości, ale nadal nie mieć atomowej transakcji biznesowej. Przykładem jest para wartości `min` i `max`, saldo dwóch kont, stan zamówienia i historia zmian, albo licznik wraz z powiązaną kolekcją.

Atomiki są wydajne, ale nie zawsze prostsze. Kod oparty na pętlach CAS może być trudniejszy do zrozumienia niż zwykła sekcja krytyczna. Przy dużej kontencji wiele wątków może wielokrotnie przegrywać CAS i powtarzać pracę. Dlatego atomiki są świetne dla prostych przypadków, ale nie powinny być nadużywane jako uniwersalny substytut blokad.

## Executor i model jednowątkowy

Executor oddziela opis zadania od sposobu jego wykonania. Zamiast ręcznie tworzyć wątki, przekazujemy zadania do kontrolowanego mechanizmu wykonawczego. Szczególnie interesujący z perspektywy bezpieczeństwa współbieżności jest jednowątkowy executor. Gdy cały dostęp do danego stanu odbywa się przez jeden executor, operacje są wykonywane sekwencyjnie, a więc wiele klasycznych problemów z wyścigami znika.

Ten model jest często niedoceniany, ponieważ wydaje się mniej „równoległy”. W rzeczywistości bywa bardzo skalowalny, jeżeli stan można podzielić na niezależne partycje. Na przykład zamiast jednej globalnej blokady można mieć wiele kolejek obsługujących różne klucze, użytkowników, agregaty lub partycje danych. Wewnątrz jednej partycji zachowujemy prosty model sekwencyjny, a równoległość uzyskujemy między partycjami.

Minusem jest konieczność pracy z `Future`, `CompletableFuture`, callbackami albo komunikatami. Błędy mogą przenieść się z poziomu data race na poziom projektowania przepływu asynchronicznego: niewłaściwa obsługa wyjątków, brak timeoutów, blokowanie wątku executora przez długie operacje I/O, niekontrolowany wzrost kolejki albo deadlock logiczny, gdy zadanie czeka na inne zadanie zaplanowane do tego samego jednowątkowego executora.

## Race condition

Race condition oznacza, że wynik programu zależy od niekontrolowanej kolejności przeplatania operacji wykonywanych przez wątki. Nie każdy race condition jest data race w sensie JMM, ale oba pojęcia często występują razem. Data race dotyczy niezsynchronizowanego dostępu do pamięci, race condition dotyczy szerszej sytuacji logicznej, w której kolejność zdarzeń zmienia wynik programu.

Typowym przykładem jest operacja „check-then-act”: sprawdź, czy warunek jest spełniony, a następnie wykonaj akcję. Jeżeli między sprawdzeniem a akcją inny wątek może zmienić stan, wynik może być błędny. Podobny problem występuje w operacji „read-modify-write”, czyli odczytaj wartość, oblicz nową, zapisz. Obie kategorie wymagają atomowego objęcia całej sekwencji jednym mechanizmem synchronizacji albo zastąpienia jej gotową operacją atomową dostarczoną przez strukturę współbieżną.

Ważne jest, aby nie mylić braku wyjątku z poprawnością. Race condition może nie powodować awarii programu, lecz subtelną utratę danych, zduplikowanie operacji, błędne saldo, niepoprawny status albo rzadkie naruszenie reguły biznesowej. Takie błędy są szczególnie kosztowne, bo często ujawniają się dopiero w produkcji.

## Deadlock, livelock i starvation

Deadlock występuje wtedy, gdy dwa lub więcej wątków czeka na siebie nawzajem i żaden nie może kontynuować. Klasyczny scenariusz to przejmowanie dwóch blokad w różnej kolejności. Wątek A posiada lock 1 i czeka na lock 2, a wątek B posiada lock 2 i czeka na lock 1. Program nie zużywa intensywnie CPU, ale przestaje robić postęp.

Podstawową metodą zapobiegania deadlockom jest ustalenie globalnej kolejności przejmowania blokad. Jeżeli każdy fragment systemu przejmuje blokady zawsze w tej samej kolejności, cykl oczekiwania nie powstanie. Inną strategią jest ograniczenie liczby blokad, użycie `tryLock` z timeoutem, unikanie wywołań obcego kodu wewnątrz sekcji krytycznej oraz projektowanie komponentów tak, aby nie wymagały zagnieżdżonych blokad.

Livelock różni się od deadlocku tym, że wątki nie są formalnie zablokowane, ale stale reagują na siebie w sposób uniemożliwiający postęp. Starvation oznacza, że jeden wątek przez długi czas nie dostaje zasobu, ponieważ inne wątki stale go wyprzedzają. Te problemy są rzadsze w prostych aplikacjach, ale ważne w systemach o dużej kontencji, priorytetach, kolejkach i ograniczonych pulach zasobów.

## Kolekcje współbieżne

Standardowe kolekcje, takie jak `ArrayList`, `HashMap` czy `HashSet`, nie są bezpieczne do współdzielonej mutacji bez synchronizacji. Nie oznacza to, że nigdy nie można ich używać w programie wielowątkowym. Można, jeśli są ograniczone do jednego wątku, są niemutowalne po publikacji, albo cały dostęp do nich jest chroniony spójną blokadą. Problemem nie jest sama klasa, lecz sposób użycia.

Kolekcje z `java.util.concurrent`, takie jak `ConcurrentHashMap`, `CopyOnWriteArrayList` czy `BlockingQueue`, rozwiązują konkretne problemy. `ConcurrentHashMap` pozwala na bezpieczny współbieżny dostęp do mapy i oferuje operacje atomowe, takie jak `computeIfAbsent`. `BlockingQueue` świetnie nadaje się do komunikacji producent-konsument. `CopyOnWriteArrayList` sprawdza się, gdy odczytów jest bardzo dużo, a zapisów bardzo mało, na przykład w liście listenerów.

Nie należy jednak zakładać, że użycie kolekcji współbieżnej automatycznie czyni cały algorytm poprawnym. Jeżeli wykonujemy kilka operacji na mapie i oczekujemy, że cała sekwencja będzie atomowa, musimy użyć odpowiedniej metody atomowej, takiej jak `compute`, `merge` lub `putIfAbsent`, albo zapewnić dodatkową synchronizację. Bez tego można mieć bezpieczną kolekcję, ale niebezpieczną logikę.

## Testowanie kodu współbieżnego

Testowanie współbieżności jest trudne, ponieważ błędy zależą od harmonogramu wykonania wątków. Test, który przechodzi sto razy, nie dowodzi poprawności. Test, który czasem zawodzi, jest bardzo wartościowy, ale jego niestabilność utrudnia diagnostykę. Dlatego testy współbieżne powinny maksymalnie kontrolować moment startu wątków, punkty synchronizacji i warunki zakończenia.

`CountDownLatch` jest jednym z najprostszych narzędzi do reprodukowania wyścigów. Można użyć jednej zapadki do przygotowania wielu wątków, drugiej do jednoczesnego startu i trzeciej do oczekiwania na zakończenie. Dzięki temu zwiększamy szansę na realne współbieżne wykonanie krytycznego fragmentu. Podobną rolę mogą pełnić `CyclicBarrier`, `Phaser`, `Semaphore` i `ExecutorService`.

W testach należy unikać ślepego polegania na `Thread.sleep`. Sen wątku nie daje gwarancji konkretnego przeplotu operacji. Może działać na lokalnej maszynie, a zawodzić na CI albo odwrotnie. Lepsze są jawne punkty synchronizacji, timeouty zapobiegające zawieszeniu testu oraz powtarzanie scenariuszy. W bardziej zaawansowanych przypadkach warto używać narzędzi typu JCStress, które zostały zaprojektowane specjalnie do testowania zachowań wynikających z JMM.

Testy powinny sprawdzać nie tylko brak wyjątków, ale niezmienniki. Dla licznika będzie to oczekiwana końcowa wartość. Dla przelewów suma sald. Dla kolejki liczba przetworzonych komunikatów i brak duplikatów. Dla cache — brak wielokrotnego tworzenia tego samego kosztownego obiektu, jeżeli logika tego zabrania. Test współbieżny bez jasno zdefiniowanego niezmiennika często daje fałszywe poczucie bezpieczeństwa.

## Porównanie modeli współbieżności

`synchronized` jest najprostszy koncepcyjnie i zwykle wystarczający dla małych sekcji krytycznych. Jego czytelność jest dobra, o ile polityka blokowania jest konsekwentna. Ryzyko deadlocku pojawia się głównie wtedy, gdy używa się wielu monitorów albo wywołuje obcy kod wewnątrz sekcji krytycznych. Skalowalność zależy od długości sekcji krytycznej i poziomu kontencji.

`ReentrantLock` oferuje większą kontrolę, ale zwiększa złożoność kodu. Jest dobry, gdy potrzebujemy timeoutów, przerwania oczekiwania, `tryLock`, wielu warunków albo sprawiedliwości. W prostych przypadkach bywa gorszym wyborem niż `synchronized`, ponieważ wymaga ręcznego zarządzania cyklem życia blokady. Jego testowalność jest dobra, ale błędy w obsłudze `unlock()` mogą być poważne.

`AtomicInteger` i inne atomiki są bardzo dobre dla prostych, niezależnych wartości. Są zwykle czytelne przy licznikach i flagach, skalują się dobrze przy umiarkowanej kontencji i eliminują klasyczne deadlocki, ponieważ nie używają blokad w tradycyjnym sensie. Ich słabością jest modelowanie złożonych niezmienników. Wtedy kod może stać się trudniejszy niż rozwiązanie z jedną dobrze opisaną blokadą.

Jednowątkowy `Executor` upraszcza bezpieczeństwo stanu przez serializację dostępu. Zamiast chronić stan przed równoległymi modyfikacjami, usuwamy równoległe modyfikacje tego stanu. To bardzo atrakcyjny model architektoniczny, szczególnie dla komponentów stanowych. Jego słabością jest potencjalne wąskie gardło, konieczność obsługi asynchroniczności i ryzyko zablokowania executora przez zadanie, które nie powinno się w nim wykonywać.

W uproszczeniu: `synchronized` jest dobrym domyślnym wyborem dla prostych sekcji krytycznych, `ReentrantLock` dla zaawansowanych protokołów blokowania, atomiki dla prostych wartości i liczników, a jednowątkowy executor dla komponentów, w których warto zamienić współdzielony stan na sekwencyjne przetwarzanie komunikatów.

## Kompromisy architektoniczne

W projektowaniu systemu współbieżnego najważniejsze pytanie brzmi nie „jakiej blokady użyć?”, lecz „czy ten stan musi być współdzielony?”. Jeżeli odpowiedź brzmi „nie”, najlepszym rozwiązaniem jest unikanie współdzielenia. Jeżeli odpowiedź brzmi „tak”, trzeba ustalić właściciela stanu, mechanizm ochrony i niezmienniki. Kod powinien jasno komunikować, czy dana klasa jest niemutowalna, thread-safe, warunkowo thread-safe, czy w ogóle nie jest przeznaczona do współdzielenia między wątkami.

Dobra klasa thread-safe ukrywa synchronizację wewnątrz. Użytkownik klasy nie powinien musieć wiedzieć, że musi samodzielnie wykonać `synchronized` na konkretnym obiekcie przed wywołaniem dwóch metod w określonej kolejności, chyba że dokumentacja bardzo wyraźnie opisuje taki kontrakt. Jeżeli poprawne użycie klasy wymaga skomplikowanego protokołu po stronie klienta, projekt jest podatny na błędy.

Warto też rozróżniać bezpieczeństwo techniczne od bezpieczeństwa biznesowego. Można mieć kod bez data race, który nadal łamie reguły domenowe, bo operacje są atomowe na złym poziomie abstrakcji. Przelew bankowy, rezerwacja miejsca, zmiana statusu zamówienia czy aktualizacja limitu wymagają myślenia w kategoriach transakcji logicznej, a nie tylko pojedynczego pola.

## Kryterium done

Temat można uznać za opanowany, gdy potrafisz wyjaśnić różnicę między widocznością a atomowością oraz wskazać, dlaczego `volatile` nie wystarcza dla `counter++`. Powinieneś umieć opisać relację happens-before i podać praktyczne przykłady jej powstawania. Powinieneś rozumieć, dlaczego niepoprawna publikacja obiektu jest niebezpieczna, czemu pola `final` pomagają, ale nie rozwiązują wszystkich problemów, oraz dlaczego niemutowalność jest jedną z najważniejszych technik projektowania współbieżnego.

Powinieneś też umieć dobrać mechanizm synchronizacji do problemu: `synchronized` dla prostych sekcji krytycznych, `ReentrantLock` dla sytuacji wymagających większej kontroli, atomiki dla prostych wartości oraz executor dla serializacji dostępu do stanu. Ważne jest, abyś potrafił uzasadnić kompromisy, a nie tylko wskazać klasę z API.

Praktycznym kryterium jest zdolność napisania testu, który celowo zwiększa szansę ujawnienia race condition za pomocą `CountDownLatch` lub podobnego mechanizmu. Równie ważna jest umiejętność zidentyfikowania niezmiennika, który test ma chronić. Jeżeli test jedynie uruchamia kilka wątków i sprawdza, że nie było wyjątku, jest zbyt słaby.

Na poziomie architektury powinieneś umieć spojrzeć na komponent i odpowiedzieć na pytania: jaki stan jest mutowalny, kto jest jego właścicielem, czy stan jest współdzielony, jaka blokada go chroni, czy istnieje bezpieczna publikacja, czy operacje biznesowe są atomowe, czy możliwy jest deadlock oraz jak system zachowa się pod dużą kontencją. Dopiero takie pytania prowadzą do realnie bezpiecznego kodu współbieżnego.

## Podsumowanie

Programowanie współbieżne w Javie nie sprowadza się do użycia `synchronized`, `volatile`, atomików lub executora. Są to jedynie narzędzia służące do realizacji głębszych zasad: kontrolowania widoczności, zachowania atomowości, ochrony niezmienników, bezpiecznego publikowania obiektów i ograniczania współdzielonego mutowalnego stanu. Najlepszy kod współbieżny to często taki, który ma najmniej współdzielenia, najprostsze reguły własności i najmniejszą liczbę miejsc wymagających synchronizacji.

Z perspektywy Java Concurrency in Practice najważniejsze jest myślenie o bezpieczeństwie wątkowym jako o własności projektu, a nie dodatku na końcu implementacji. Z perspektywy Effective Java szczególnie istotne są niemutowalność, enkapsulacja, unikanie niepotrzebnego współdzielenia oraz świadome używanie bibliotek. Poprawność współbieżna wymaga dyscypliny, ponieważ błędy rzadko ujawniają się deterministycznie. Dlatego kod powinien być projektowany tak, aby był możliwie prosty do rozumienia, a nie tylko pozornie szybki.
