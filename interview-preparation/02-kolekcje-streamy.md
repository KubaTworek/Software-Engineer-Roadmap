# Kolekcje i Stream API

[Spis rozdziałów](README.md)

<!-- question-toc:start -->
## Spis pytań

- [COL-01. Jak HashMap działa wewnętrznie i co dzieje się przy kolizji?](#col-01-jak-hashmap-działa-wewnętrznie-i-co-dzieje-się-przy-kolizji)
- [COL-02. Dlaczego HashMap nie jest thread-safe? Czy sama powoduje deadlock?](#col-02-dlaczego-hashmap-nie-jest-thread-safe-czy-sama-powoduje-deadlock)
- [COL-03. Jak ConcurrentHashMap działa i jakie daje gwarancje?](#col-03-jak-concurrenthashmap-działa-i-jakie-daje-gwarancje)
- [COL-04. Dlaczego ConcurrentHashMap nie pozwala na null?](#col-04-dlaczego-concurrenthashmap-nie-pozwala-na-null)
- [COL-05. Czym różnią się `putIfAbsent`, `computeIfAbsent` i check-then-act?](#col-05-czym-różnią-się-putifabsent-computeifabsent-i-check-then-act)
- [COL-06. Dlaczego callbacki `compute()` powinny być krótkie i nie modyfikować tej samej mapy?](#col-06-dlaczego-callbacki-compute-powinny-być-krótkie-i-nie-modyfikować-tej-samej-mapy)
- [COL-07. Co się psuje, gdy mutowalny obiekt jest kluczem mapy?](#col-07-co-się-psuje-gdy-mutowalny-obiekt-jest-kluczem-mapy)
- [COL-08. Kiedy duża HashMap ma sens i jak diagnozować spadek wydajności przy milionie wpisów?](#col-08-kiedy-duża-hashmap-ma-sens-i-jak-diagnozować-spadek-wydajności-przy-milionie-wpisów)
- [COL-09. Jak ArrayList zarządza pamięcią i kiedy wybrać LinkedList?](#col-09-jak-arraylist-zarządza-pamięcią-i-kiedy-wybrać-linkedlist)
- [COL-10. Czym są iteratory fail-fast, weakly consistent i snapshot?](#col-10-czym-są-iteratory-fail-fast-weakly-consistent-i-snapshot)
- [COL-11. Czy `Collections.synchronizedList()` rozwiązuje wszystkie problemy współbieżności?](#col-11-czy-collectionssynchronizedlist-rozwiązuje-wszystkie-problemy-współbieżności)
- [COL-12. Czym różnią się Comparable i Comparator? Jak posortować osoby według wieku?](#col-12-czym-różnią-się-comparable-i-comparator-jak-posortować-osoby-według-wieku)
- [COL-13. Jak Stream API działa wewnętrznie? Czym są operacje pośrednie i terminalne?](#col-13-jak-stream-api-działa-wewnętrznie-czym-są-operacje-pośrednie-i-terminalne)
- [COL-14. Czym różnią się `map()` i `flatMap()`?](#col-14-czym-różnią-się-map-i-flatmap)
- [COL-15. Jak parallel stream dzieli pracę i kiedy pomaga?](#col-15-jak-parallel-stream-dzieli-pracę-i-kiedy-pomaga)
- [COL-16. Co robi `Collectors.toMap()` przy duplikatach i czy można zmieniać `Stream.toList()`?](#col-16-co-robi-collectorstomap-przy-duplikatach-i-czy-można-zmieniać-streamtolist)
- [COL-17. Jak znaleźć maksymalną pensję w każdym dziale przez Stream API?](#col-17-jak-znaleźć-maksymalną-pensję-w-każdym-dziale-przez-stream-api)
<!-- question-toc:end -->

## COL-01. Jak HashMap działa wewnętrznie i co dzieje się przy kolizji?

Hash klucza po mieszaniu bitów wybiera kubełek tablicy; równość klucza jest sprawdzana przez `equals`. Różne klucze mogą mieć ten sam hash i współistnieć. W implementacji od JDK 8 długie łańcuchy mogą zostać zamienione na drzewa; stałe progów to `TREEIFY_THRESHOLD=8` i minimalna pojemność tablicy 64. Dla mniejszej tablicy preferowane jest powiększenie.

Średni koszt operacji jest bliski O(1), ale zależy od rozkładu hashy, kosztu `equals` i resize. Przy przekroczeniu progu size/capacity (domyślny load factor 0,75) tablica zwykle podwaja pojemność. Szczegóły drzewienia to implementacja, nie obietnica interfejsu `Map`. Dla klucza definiuję stabilne, zgodne `equals` i `hashCode`. [Źródło HashMap w OpenJDK](https://github.com/openjdk/jdk21u/blob/master/src/java.base/share/classes/java/util/HashMap.java).

Dla tablicy o pojemności 16 próg przy load factor 0,75 wynosi 12 wpisów. Kolejny nowy wpis może uruchomić resize i rozdzielenie kubełków. To chwilowy koszt aktualizacji struktury, a nie ciągłe „kopiowanie mapy przy każdym put”. Przy budowaniu dużego indeksu można ograniczyć takie skoki przez dobranie pojemności.

Drzewienie nie naprawia kosztownego equals ani każdego przypadku patologicznych kluczy. W analizie mierzę osobno czas hashowania, porównywania, alokacji i oczekiwania na zewnętrzną blokadę. Iteracja kosztuje również przejście przez tablicę kubełków, więc ogromna pojemność dla kilku elementów ma swój koszt.

## COL-02. Dlaczego HashMap nie jest thread-safe? Czy sama powoduje deadlock?

Równoległe modyfikacje mogą powodować lost updates, brak widoczności i naruszenie struktury. Działanie w testach nie dowodzi poprawności. Bezpieczny jest np. opublikowany, niezmieniany snapshot albo pełna ochrona wszystkich dostępów jedną blokadą; do współbieżnych aktualizacji zwykle wybieram ConcurrentHashMap.

HashMap sama nie blokuje monitorów, więc nie przypisuję jej automatycznie deadlocka. Historyczne pętle podczas resize w starszych implementacjach to inny problem. Timeout może wynikać z otaczającej blokady, kosztownego `equals`, CPU albo presji pamięci — wymaga profilu.

Wyścig może istnieć także wtedy, gdy wszystkie wątki „tylko odczytują”: jeżeli mapa została opublikowana niebezpiecznie albo jej budowa nadal trwa, odczyt nie ma poprawnego punktu startowego. Bezpieczny model to zbudować kompletną mapę, zamrozić ją i opublikować przez właściwy mechanizm.

<!-- example:COL-02-1 kind:java-members -->
**Kontekst:** Java 21 — elementy klasy; kompilowane przez skrypt po osadzeniu w klasie.

~~~java
private volatile java.util.Map<String, String> routes = java.util.Map.of();
void reload(java.util.Map<String, String> loaded) {
    routes = java.util.Map.copyOf(loaded);
}
~~~

Odczyt pobiera jeden snapshot referencji. Ta technika pasuje do rzadko zmienianej konfiguracji; częste kopiowanie dużej mapy może być zbyt drogie.

## COL-03. Jak ConcurrentHashMap działa i jakie daje gwarancje?

Współczesne implementacje łączą operacje CAS i lokalną synchronizację aktualizacji kubełków; nie należy opisywać ich wyłącznie historycznymi segmentami. Odczyty zwykle nie wymagają blokady całej mapy. Zakończona aktualizacja danego klucza jest widoczna dla odczytu, który odczytał jej wynik.

Nie ma atomowego snapshotu wszystkich kluczy ani automatycznej ochrony wnętrza mutowalnej wartości. Dwie operacje `get`/`put` nadal mogą tworzyć wyścig. `size()` przy aktualizacjach nie nadaje się jako dokładna bariera biznesowa. [Kontrakt ConcurrentHashMap](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html).

Wartość mapy może psuć bezpieczeństwo nawet przy poprawnym get/put:

<!-- example:COL-03-1 kind:java-statements -->
**Kontekst:** Java 21 — instrukcje ciała metody; kompilowane przez skrypt w metodzie pomocniczej.

~~~java
var users = new java.util.concurrent.ConcurrentHashMap<String, java.util.List<String>>();
users.put("team", new java.util.ArrayList<>());
// users.get("team").add("A"); // mutacja listy nie jest chroniona przez mapę
~~~

Rozwiązaniem jest niezmienna wartość podmieniana przez compute, osobna struktura współbieżna albo jeden właściciel. Wybór zależy od operacji: aktualizacja dwóch zespołów naraz nadal wymaga szerszej granicy. Mapa zapewnia publikację własnego wpisu, ale nie obejmuje dowolnego protokołu wykonywanego po zwróceniu referencji klientowi.

<!-- follow-up:COL-03 -->
**Sprawdź rozumienie:** Czy ConcurrentHashMap<String, ArrayList<Order>> pozwala bezpiecznie dopisywać pozycje do pobranej listy?

<details>
<summary>Kierunek odpowiedzi</summary>

Mapa chroni własne operacje i publikację wpisu. Mutacja ArrayList poza atomową operacją mapy wymaga osobnej ochrony albo podmiany niezmiennej wartości. Dwa klucze nie tworzą wspólnej transakcji.

</details>

## COL-04. Dlaczego ConcurrentHashMap nie pozwala na null?

`null` oznacza brak wyniku i pozwala jednoznacznie interpretować współbieżny `get`. Gdyby null był prawidłową wartością, dodatkowy `containsKey` nie rozwiązałby problemu: między operacjami inny wątek może zmienić mapę.

Brak wartości modeluję przez usunięcie wpisu albo jawny obiekt reprezentujący wynik negatywny. Takie negatywne cache wymaga ograniczenia czasu i rozmiaru. Nie zastępuję null losowym sentinelem, który może kolidować z prawidłową wartością domenową.

Przy cache negatywnym trzeba odróżnić „jeszcze nie pytaliśmy” od „pytaliśmy i brak”. Można przechować jawny wynik Found/NotFound albo Optional jako wartość. Brak wpisu uruchamia odczyt, NotFound ogranicza ponawianie dla nieistniejącego klucza.

Dla awarii sieci nie zapisuję automatycznie NotFound: brak odpowiedzi nie oznacza braku rekordu. Osobny TTL wyniku negatywnego ogranicza opóźnienie po utworzeniu nowego zasobu. Unikalne atakujące klucze również wymagają limitu rozmiaru, bo negatywny cache może wyczerpać pamięć równie skutecznie jak zwykły.

## COL-05. Czym różnią się `putIfAbsent`, `computeIfAbsent` i check-then-act?

`containsKey` i późniejszy `put` nie są razem atomowe. `putIfAbsent` atomowo wstawia już przygotowaną wartość, jeżeli klucza brak; obliczenie wartości następuje wcześniej. `computeIfAbsent` oblicza ją na żądanie w ramach atomowej operacji dla klucza.

Przy równoczesnych wywołaniach nie trzeba tworzyć dwóch zapisanych wartości, ale brak wyniku, wyjątek lub późniejsze usunięcie dopuszczają kolejne obliczenie. Nie traktuję callbacka jako gwarantowanego „raz na zawsze” miejsca na obciążenie karty.

Różnicę między gotową wartością i fabryką pokazuje fragment:

<!-- example:COL-05-1 kind:java-sketch -->
**Kontekst:** Szkic Java — wymaga wskazanych pól lub metod otoczenia; poza automatyczną kompilacją.

~~~java
var map = new java.util.concurrent.ConcurrentHashMap<String, String>();
map.putIfAbsent("A", expensiveValue());          // obliczenie nastąpi zawsze
map.computeIfAbsent("B", key -> expensiveValue()); // obliczenie na brak
~~~

To szkic z domenową funkcją expensiveValue. Nawet przy computeIfAbsent koszt dla innych kluczy zależy od wewnętrznej synchronizacji i rozkładu hashy. Jeżeli fabryka zwróci null, wpis nie powstaje. Przy wyjątku następny caller może spróbować ponownie. Z tego powodu nie umieszczam tam nieodwracalnej operacji, której powtórzenie byłoby błędem biznesowym.

## COL-06. Dlaczego callbacki `compute()` powinny być krótkie i nie modyfikować tej samej mapy?

Obliczenie jest częścią koordynowanej aktualizacji; długie I/O może zatrzymać aktualizacje tego samego obszaru mapy. Rekurencyjna modyfikacja narusza kontrakt callbacka i może zakończyć się błędem, np. wykrytym `IllegalStateException`, lub problemem postępu.

Dla kosztownego pobrania danych stosuję osobny mechanizm single-flight, np. współdzielony Future z ustalonym sprzątaniem po błędzie. Nie utrzymuję wewnętrznej blokady mapy podczas HTTP ani nie buduję zależności blokad przez nieznany callback.

Przykład zagrożenia: callback dla klucza A wywołuje usługę, która pośrednio próbuje obliczyć B, a inny wątek robi odwrotnie. Pozornie niewinne compute może wciągnąć zależności i blokady spoza mapy. Problemem jest cały graf wywołań, nie tylko linia z map.put.

Single-flight przechowujący Future wymaga usunięcia błędnego lub wygasłego wyniku, ograniczenia liczby kluczy i decyzji o anulowaniu. Anulowanie oczekiwania jednego klienta nie powinno automatycznie przerwać pracy potrzebnej pozostałym. Testuję też wyjątek przed rejestracją wyniku i równoczesne odświeżenia.

## COL-07. Co się psuje, gdy mutowalny obiekt jest kluczem mapy?

Zmiana pola używanego przez `equals` lub `hashCode` może spowodować, że klucz zostaje fizycznie w starym kubełku, a wyszukiwanie wybiera nowy. Efektem są nieodnajdywane wpisy i pozorne duplikaty. ConcurrentHashMap nie naprawia naruszonego kontraktu klucza.

Stosuję niezmienne ID lub value object. Jeżeli zmiana klucza jest konieczna, usuwam wpis według starego klucza przed zmianą i ponownie wstawiam, z odpowiednią synchronizacją całej operacji.

Jeżeli hash klucza wyliczano z adresu, po zmianie ulicy get może szukać w innym kubełku. Nawet wywołanie get tym samym obiektem nie jest ratunkiem: mapa najpierw wykorzystuje hash. Klucz może też złamać porządek w TreeMap, gdy zmieniono pole komparatora.

<!-- example:COL-07-1 kind:java-statements -->
**Kontekst:** Java 21 — instrukcje ciała metody; kompilowane przez skrypt w metodzie pomocniczej.

~~~java
record CustomerKey(long tenantId, long customerId) {}
~~~

Takie ID ma stabilną równość i nie zmienia się wraz z profilem użytkownika. Mutowalność samej wartości jest osobnym problemem. Test kontraktu powinien pokazać, które pola należą do tożsamości i nie mogą się zmieniać w okresie obecności klucza w indeksie.

## COL-08. Kiedy duża HashMap ma sens i jak diagnozować spadek wydajności przy milionie wpisów?

Ma sens dla częstych odczytów po kluczu, gdy cały indeks mieści się w budżecie pamięci. Sprawdzam kolizje, koszt hashowania, resize, liczbę obiektów, GC, lokalność pamięci oraz blokadę chroniącą mapę. Milion wpisów samo w sobie nie określa problemu.

Dobieram początkową pojemność do liczby wpisów i load factor, ale unikam nadmiernej rezerwacji. Cache musi mieć limit rozmiaru; mapa z niepowtarzalnym request ID jako kluczem może stale rosnąć bez korzyści z odczytów.

Rozmiar pamięci to nie suma samych payloadów: dochodzą tablica kubełków, węzły, referencje, obiekty kluczy i wyrównanie. Dla miliona krótkich rekordów narzut może dominować. Heap dump pokazuje retencję, a profil CPU koszt porównań; jednego nie zastępuje drugi.

Przykładowo rosnąca liczba unikalnych request ID daje niski reuse i nieograniczony wzrost. Limit capacity z odpowiednią polityką usuwa przyczynę, podczas gdy większy heap tylko opóźnia awarię. Dla stałego słownika czasem lepsze są tablice lub kompaktowy indeks, ale decyzja wymaga pomiaru wzorca operacji.

## COL-09. Jak ArrayList zarządza pamięcią i kiedy wybrać LinkedList?

ArrayList przechowuje referencje w tablicy. Dostęp po indeksie jest O(1), dodawanie na końcu amortyzowane O(1), a resize kopiuje elementy. Wstawienie w środku wymaga przesunięcia. LinkedList przechowuje węzły i ma O(n) wyszukiwania pozycji oraz większy narzut pamięci.

Usunięcie znanego węzła jest tanie, ale znalezienie go po indeksie już nie. W praktyce ArrayList zwykle wygrywa lokalnością i liczbą alokacji. Do kolejki/deque często lepszy jest ArrayDeque niż LinkedList.

Dla operacji „usuń element o indeksie n/2” LinkedList musi najpierw dojść do węzła, więc nadal wykonuje O(n) kroków. Przy znanym iteratorze lokalne usunięcie może być O(1), ale znalezienie pozycji nie znika z całkowitego kosztu.

ArrayList dodatkowo kopiuje referencje, a nie pełne obiekty elementów. Sekwencyjne odczyty tablicy zwykle lepiej wykorzystują cache CPU niż skakanie po węzłach. Dobieram strukturę do miksu operacji: losowe odczyty, append, deque, usuwanie po ID. „Częste inserty” bez wskazania miejsca i sposobu znalezienia pozycji to za mało do wyboru listy.

## COL-10. Czym są iteratory fail-fast, weakly consistent i snapshot?

Fail-fast oznacza próbę wykrycia modyfikacji niezgodnej z protokołem iteratora, zwykle przez ConcurrentModificationException. Może wystąpić także w jednym wątku, gdy foreach i modyfikacja kolekcji nie współpracują. Wykrywanie jest best-effort: brak wyjątku nie dowodzi braku wyścigu ani spójności wyniku.

Iterator ConcurrentHashMap jest weakly consistent: toleruje aktualizacje i może widzieć część zmian, ale nie reprezentuje jednego punktu w czasie. CopyOnWriteArrayList iteruje natomiast po snapshotcie tablicy. Każda aktualizacja kopiuje tablicę, a długo żyjący iterator utrzymuje starszą wersję w pamięci.

Dlatego dla małego, często odczytywanego rejestru listenerów sensowny bywa copy-on-write, dla monitoringu aktualizowanej mapy weakly consistent, a dla spójnego raportu potrzebny jest jawny snapshot lub transakcja. Potoczne „fail-safe” nie rozróżnia tych gwarancji. Wyboru dokonuję według wymaganej obserwacji danych i kosztu zapisów, a nie samego braku ConcurrentModificationException.

## COL-11. Czy `Collections.synchronizedList()` rozwiązuje wszystkie problemy współbieżności?

Wrapper synchronizedList synchronizuje pojedyncze operacje wspólnym monitorem. Iteracja i sekwencja „sprawdź brak, potem dodaj” wymagają ochrony całej logicznej operacji. Dostęp do surowej listy z pominięciem wrappera narusza ten kontrakt.

<!-- example:COL-11-1 kind:java-statements -->
**Kontekst:** Java 21 — instrukcje ciała metody; kompilowane przez skrypt w metodzie pomocniczej.

~~~java
var list = java.util.Collections.synchronizedList(new java.util.ArrayList<String>());
synchronized (list) {
    for (String value : list) {
        System.out.println(value);
    }
}
~~~

To ilustracja granicy monitora, nie zalecenie wolnego logowania pod lockiem. Gdy przetwarzanie jest kosztowne, kopiuję listę w krótkiej sekcji, a snapshot przetwarzam po wyjściu. Dostaję wtedy stan listy z określonego momentu, ale tylko kopię referencji: zmienne elementy nadal potrzebują ochrony lub własnych snapshotów. Długie callbacki pod blokadą zwiększają contention i mogą wprowadzić zależności prowadzące do deadlocka.

## COL-12. Czym różnią się Comparable i Comparator? Jak posortować osoby według wieku?

Comparable definiuje porządek naturalny typu, a Comparator zewnętrzną strategię sortowania, np. wiek, potem ID. Dla osób wybieram porównanie po wieku i stabilny tie-breaker, jeżeli wynik ma być deterministyczny. Nie odejmuję intów w komparatorze, bo grozi przepełnienie.

Niespójny komparator może łamać założenia sortowania i struktur drzewiastych. W TreeSet porównanie równe zero oznacza ten sam element z perspektywy zbioru, nawet gdy `equals` zwraca false. Politykę null określam jawnie.

Dla uporządkowania po wieku i identyfikatorze zapis jest bezpośredni:

<!-- example:COL-12-1 kind:java-statements -->
**Kontekst:** Java 21 — instrukcje ciała metody; kompilowane przez skrypt w metodzie pomocniczej.

~~~java
record Person(long id, int age) {}
var byAge = java.util.Comparator.comparingInt(Person::age)
    .thenComparingLong(Person::id);
~~~

Dodatkowy klucz stabilizuje wynik między odczytami, ale nie naprawi zmiany danych w trakcie paginacji. Jeśli sortowana kolekcja dopuszcza null, określam nullsFirst/nullsLast. Warto także wiedzieć, czy comparator jest zgodny z equals: w sortowaniu listy rozbieżność bywa dopuszczalna, w zbiorze drzewiastym może spowodować odrzucenie elementu. Nie korzystam z pól, które zmieniają się bez aktualizacji indeksu.

## COL-13. Jak Stream API działa wewnętrznie? Czym są operacje pośrednie i terminalne?

Stream opisuje leniwy potok nad źródłem. Operacje pośrednie, np. `filter` i `map`, budują przetwarzanie, a terminalna, np. `collect` lub `count`, uruchamia potrzebną pracę. Operacje stanowe, np. `sorted`, mogą wymagać buforowania.

Stream konsumuje się raz; ponowne użycie zwykle kończy się `IllegalStateException`. Tworzę nowy strumień ze źródła. Nie opieram poprawności na efektach ubocznych w `peek`, bo optymalizacja i skracanie obliczeń mogą pominąć oczekiwaną pracę.

Pipeline może łączyć kilka operacji w jedno przejście: element przechodzi przez filter i map dopiero wtedy, gdy terminalna operacja żąda kolejnego wyniku. Short-circuit, np. findFirst, może zakończyć wcześniej, natomiast sorted potrzebuje poznać większą część wejścia.

<!-- example:COL-13-1 kind:java-statements -->
**Kontekst:** Java 21 — instrukcje ciała metody; kompilowane przez skrypt w metodzie pomocniczej.

~~~java
long active = java.util.List.of("A", "", "B").stream()
    .filter(value -> !value.isEmpty())
    .count(); // 2
~~~

Strumień nie jest materializowaną kolekcją wyników. Dla I/O, np. Files.lines, ma również zasób do zamknięcia. Rozdzielam więc wygodną transformację od lifecycle źródła i kosztu operacji stanowych.

## COL-14. Czym różnią się `map()` i `flatMap()`?

`map` zamienia jeden element na jeden wynik, także wynik będący listą. `flatMap` zamienia element na strumień i spłaszcza wyniki, np. zamówienia na ich pozycje. Rozwiązuje problem zagnieżdżonych kontenerów, ale może znacząco zwiększyć liczbę przetwarzanych elementów.

Przed spłaszczeniem filtruję tam, gdzie pozwala semantyka. Nie używam `flatMap` do ukrywania zdalnego N+1: jedno zapytanie HTTP na element nadal kosztuje, choć zapis wygląda jak elegancki pipeline.

Dla listy zamówień map(Order::items) tworzy strumień list, a flatMap(order -> order.items().stream()) strumień pozycji. W pierwszym wariancie liczność odpowiada liczbie zamówień, w drugim sumie ich pozycji. Ma to znaczenie dla późniejszego count, limit i pamięci.

Jeżeli items jest relacją JPA lazy, oba zapisy mogą wyzwalać osobny SQL na zamówienie. Naprawa leży wtedy w planie pobrania/projekcji, nie w zamianie metody strumienia. Test wydajności mierzy liczbę zapytań i transfer, a test semantyki obejmuje puste listy i zamówienia z wieloma pozycjami.

## COL-15. Jak parallel stream dzieli pracę i kiedy pomaga?

Spliterator dzieli źródło na części, zadania wykonują przetwarzanie i łączą wyniki; standardowo praca korzysta z common ForkJoinPool. Równoległość pomaga dla dostatecznie dużej, dzielącej się pracy CPU z niezależnymi elementami.

Koszty dzielenia, łączenia, kolejności i contention mogą przewyższyć zysk. Nie blokuję wspólnej puli długimi wywołaniami JDBC/HTTP. Zwykła ArrayList może być źródłem, jeśli nikt jej równolegle nie modyfikuje; brak thread-safety źródła nie zabrania bezpiecznego odczytu stabilnego snapshotu.

Dobry kandydat do parallel stream ma tani, równomierny podział, dużo niezależnych obliczeń i operację łączenia zgodną z wymaganą asocjatywnością. Przykładowo sumowanie liczb zmiennoprzecinkowych inną kolejnością może zmienić ostatnie bity wyniku, nawet bez race condition.

Zły przykład to dodawanie rezultatów do wspólnej ArrayList z forEach. Collector z poprawnym combinerem pozwala frameworkowi zarządzać częściowymi wynikami. Pomiar wykonuję razem z innymi zadaniami używającymi common pool, bo mikrobenchmark samodzielnego pipeline nie pokazuje contention całej usługi. Samo parallel nie jest kontraktem konkretnej liczby wątków.

## COL-16. Co robi `Collectors.toMap()` przy duplikatach i czy można zmieniać `Stream.toList()`?

Wariant `toMap` z dwoma argumentami rzuca `IllegalStateException` przy równych kluczach. Potrzebna jest świadoma funkcja scalająca albo `groupingBy`, jeśli domena dopuszcza wiele elementów na klucz. Nie wybieram przypadkowo „ostatni wygrywa”, gdy oznacza utratę danych.

Lista z `Stream.toList()` jest niemodyfikowalna: `add` rzuci `UnsupportedOperationException`. Nie jest przez to głęboko niezmienna. Jeśli potrzebuję mutowalnej ArrayList, żądam jej jawnie przez collector z fabryką kontenera.

Jawna polityka scala powtórzone klucze:

<!-- example:COL-16-1 kind:java-statements -->
**Kontekst:** Java 21 — instrukcje ciała metody; kompilowane przez skrypt w metodzie pomocniczej.

~~~java
record Sale(String sku, int quantity) {}
var totals = java.util.List.of(new Sale("A", 2), new Sale("A", 3)).stream()
    .collect(java.util.stream.Collectors.toMap(
        Sale::sku, Sale::quantity, Integer::sum)); // A -> 5
~~~

Sumowanie ma sens dla ilości, ale nie dla dwóch sprzecznych wersji profilu klienta. Tam potrzebne są wersja, konflikt lub inny kontrakt. Przy tworzeniu listy do późniejszej edycji wybieram toCollection(ArrayList::new); nie polegam też na przypadkowej mutowalności listy zwracanej przez inny collector. API powinno jasno określać uprawnienia klienta do modyfikacji.

<!-- follow-up:COL-16 -->
**Sprawdź rozumienie:** Czy merge function wybierająca „ostatnią” wartość daje deterministyczny wynik także po zmianie źródła i równoległości?

<details>
<summary>Kierunek odpowiedzi</summary>

Trzeba określić porządek i semantykę duplikatu. Funkcja łączenia musi spełniać kontrakt collectora; dla konfliktujących wersji bezpieczniejsza jest jawna wersja lub odmowa niż przypadkowy zwycięzca.

</details>

## COL-17. Jak znaleźć maksymalną pensję w każdym dziale przez Stream API?

Grupuję pracowników według ID działu i redukuję każdą grupę przez maksimum pensji, np. `groupingBy` z `maxBy` albo `toMap` z funkcją scalającą. Ustalam, czy wynik ma zawierać kwotę, jednego pracownika czy wszystkich remisujących.

Dla pieniędzy określam walutę i sposób porównania. Rozwiązanie jest O(n) czasu i O(d) pamięci dla d działów przy przechowywaniu jednego maksimum. Jeśli dane są w bazie, agregacja SQL często oszczędza transfer i alokacje w JVM.

Wariant zwracający maksymalną kwotę ma prostą redukcję:

<!-- example:COL-17-1 kind:java-statements -->
**Kontekst:** Java 21 — instrukcje ciała metody; kompilowane przez skrypt w metodzie pomocniczej.

~~~java
record Employee(String department, long salaryInCents) {}
var max = java.util.List.of(
    new Employee("IT", 100), new Employee("IT", 150)).stream()
    .collect(java.util.stream.Collectors.toMap(
        Employee::department, Employee::salaryInCents, Long::max));
~~~

Jeżeli oczekujemy nazwisk wszystkich najlepiej zarabiających, samo maksimum zgubi remisy i potrzebny jest inny wynik. Dla działu bez pracowników nie pojawi się wpis, chyba że osobno zasilimy listę działów. Nie mieszam walut; „maksymalna pensja” bez jednostki i okresu może być niepoprawnym porównaniem mimo prawidłowego algorytmu.

<!-- labs:start -->
## Powiązane laboratoria

Poniższe materiały rozwijają wybrane mechanizmy rozdziału. Każde laboratorium ma własne założenia, ograniczenia i komendy weryfikacji.

- [ConcurrentHashMap i atomowe ładowanie cache](../backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_a/concurrent_hash_map/README.md)
- [array vs linked](../backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_b/array_vs_linked/README.md)
- [stream vs loop](../backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_b/stream_vs_loop/README.md)
<!-- labs:end -->
