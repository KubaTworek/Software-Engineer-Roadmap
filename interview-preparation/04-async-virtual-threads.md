# Wykonanie asynchroniczne i virtual threads

[Spis rozdziałów](README.md)

<!-- question-toc:start -->
## Spis pytań

- [ASYNC-01. Czym różnią się Runnable, Callable, `start()` i `run()`?](#async-01-czym-różnią-się-runnable-callable-start-i-run)
- [ASYNC-02. Jakie pule oferuje Java i co dzieje się po zapełnieniu kolejki?](#async-02-jakie-pule-oferuje-java-i-co-dzieje-się-po-zapełnieniu-kolejki)
- [ASYNC-03. Dlaczego zadanie czekające na inne zadanie w tej samej puli może zawiesić usługę?](#async-03-dlaczego-zadanie-czekające-na-inne-zadanie-w-tej-samej-puli-może-zawiesić-usługę)
- [ASYNC-04. Jak CompletableFuture wybiera wątek i executor?](#async-04-jak-completablefuture-wybiera-wątek-i-executor)
- [ASYNC-05. Jak przyspieszyć API wykonujące trzy niezależne wywołania downstream?](#async-05-jak-przyspieszyć-api-wykonujące-trzy-niezależne-wywołania-downstream)
- [ASYNC-06. Jak obsłużyć timeout, anulowanie i częściową awarię zadań?](#async-06-jak-obsłużyć-timeout-anulowanie-i-częściową-awarię-zadań)
- [ASYNC-07. Jak virtual threads działają i kiedy wybrać je zamiast puli platform threads?](#async-07-jak-virtual-threads-działają-i-kiedy-wybrać-je-zamiast-puli-platform-threads)
- [ASYNC-08. Dlaczego po migracji do virtual threads throughput rośnie, a p99 się pogarsza?](#async-08-dlaczego-po-migracji-do-virtual-threads-throughput-rośnie-a-p99-się-pogarsza)
- [ASYNC-09. Co się dzieje, gdy tysiące virtual threads wyczerpią pulę DB?](#async-09-co-się-dzieje-gdy-tysiące-virtual-threads-wyczerpią-pulę-db)
- [ASYNC-10. Jak diagnozować pinning w Javie 21 i dlaczego wersja JDK ma znaczenie?](#async-10-jak-diagnozować-pinning-w-javie-21-i-dlaczego-wersja-jdk-ma-znaczenie)
- [ASYNC-11. Dlaczego CPU nadal jest wysokie przy virtual threads?](#async-11-dlaczego-cpu-nadal-jest-wysokie-przy-virtual-threads)
- [ASYNC-12. Jak ThreadLocal i stosy virtual threads wpływają na pamięć?](#async-12-jak-threadlocal-i-stosy-virtual-threads-wpływają-na-pamięć)
- [ASYNC-13. Jak obsłużyć 10 000 równoczesnych żądań i nie zalać zewnętrznego API?](#async-13-jak-obsłużyć-10-000-równoczesnych-żądań-i-nie-zalać-zewnętrznego-api)
- [ASYNC-14. Jak migrować Spring Boot do virtual threads?](#async-14-jak-migrować-spring-boot-do-virtual-threads)
- [ASYNC-15. Czy virtual threads zastępują WebFlux i Reactive Streams?](#async-15-czy-virtual-threads-zastępują-webflux-i-reactive-streams)
- [ASYNC-16. Jak backpressure chroni wolnego konsumenta?](#async-16-jak-backpressure-chroni-wolnego-konsumenta)
- [ASYNC-17. Jak przetwarzać rekordy Kafka z virtual threads?](#async-17-jak-przetwarzać-rekordy-kafka-z-virtual-threads)
<!-- question-toc:end -->

## ASYNC-01. Czym różnią się Runnable, Callable, `start()` i `run()`?

Runnable nie zwraca wyniku i nie deklaruje checked exception; Callable zwraca wynik i może rzucić wyjątek, zwykle odbierany przez Future. `start()` rozpoczyna wykonanie w nowym wątku, a bezpośrednie `run()` jest zwykłym wywołaniem na bieżącym wątku.

Wątki można uruchamiać przez Thread/Thread.Builder, executory i API komponowania zadań. Dziedziczenie po Thread zwykle nie jest potrzebne. W serwisie produkcyjnym trzeba określić właściciela executora, jego zamknięcie, limit pracy i sposób obserwacji wyjątków.

Przy submit wyjątek z Callable jest zapisany w Future i ujawnia się przy get jako ExecutionException; pominięcie odbioru wyniku może ukryć awarię przed logami aplikacji. Przy execute nie ma tego samego kontraktu Future, więc trzeba rozumieć uncaught exception handling i konfigurację executora.

<!-- example:ASYNC-01-1 kind:java-statements -->
**Kontekst:** Java 21 — instrukcje ciała metody; kompilowane przez skrypt w metodzie pomocniczej.

~~~java
java.util.concurrent.Callable<Integer> task = () -> 42;
// Future<Integer> result = executor.submit(task);
// int answer = result.get();
~~~

To fragment składania taska. Rozdzielam funkcję od mechanizmu wykonania: tę samą logikę można wykonać synchronicznie, w puli lub w virtual thread. Test samej funkcji nie potwierdza lifecycle ani limitów wykonawcy.

## ASYNC-02. Jakie pule oferuje Java i co dzieje się po zapełnieniu kolejki?

Fixed pool ogranicza wątki, lecz typowa fabryka ma nieograniczoną kolejkę; cached pool może gwałtownie zwiększać liczbę wątków. Są też pule single-thread, scheduled i work-stealing. Jawny ThreadPoolExecutor pozwala dobrać kolejkę, maksimum oraz politykę odrzucania.

Gdy nie można zakolejkować ani uruchomić kolejnego zadania, działa RejectedExecutionHandler. AbortPolicy rzuca wyjątek, CallerRuns obciąża wywołującego, discard może zgubić pracę. W API mapuję przeciążenie na kontrolowaną odmowę, nie ukryty sukces. Monitoruję czas oczekiwania i wiek zadań.

ThreadPoolExecutor najpierw może tworzyć wątki do corePoolSize, potem kolejkować, a dopiero przy nieudanym kolejkowaniu zwiększać do maximumPoolSize. Dlatego nieograniczona kolejka może sprawić, że ustawione wysokie maximum nigdy nie zostanie wykorzystane.

<!-- example:ASYNC-02-1 kind:java-statements -->
**Kontekst:** Java 21 — instrukcje ciała metody; kompilowane przez skrypt w metodzie pomocniczej.

~~~java
var executor = new java.util.concurrent.ThreadPoolExecutor(
    4, 8, 30, java.util.concurrent.TimeUnit.SECONDS,
    new java.util.concurrent.ArrayBlockingQueue<>(100),
    new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());
~~~

To przykład konfiguracji, nie uniwersalny sizing. CallerRuns na wątku event loop albo konsumencie Kafka może zablokować zupełnie inną część systemu. Politykę odrzucania testuję razem z API, shutdown i metrykami. [Kontrakt ThreadPoolExecutor](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html).

## ASYNC-03. Dlaczego zadanie czekające na inne zadanie w tej samej puli może zawiesić usługę?

Jeżeli wszystkie wątki puli czekają na Future zadań zakolejkowanych w tej samej puli, żadne z tych zadań nie dostanie wykonawcy. To starvation/deadlock zasobowy, nawet bez cyklu monitorów. Zwiększenie puli jedynie przesuwa granicę.

Unikam blokującego `get` wewnątrz tej samej ograniczonej puli: komponuję etapy albo rozdzielam rodzaje pracy. Timeout ogranicza skutek, ale trzeba także anulować zbędną pracę i nie trzymać przy oczekiwaniu połączeń ani blokad.

Przykład: pula ma dwa wątki, oba wykonują rodziców, każdy zgłasza dziecko i wywołuje get. Dzieci stoją w kolejce, bo oba wątki są zajęte czekaniem rodziców. Rozwiązanie z trzecią nitką działa tylko do następnego wzrostu fan-out.

W CompletableFuture thenCompose pozwala opisać zależność bez zajmowania wątku podczas oczekiwania na wynik. Rozdzielenie puli rodziców i dzieci może pomóc, ale nadal wymaga limitów i unikania cyklicznych zależności. W thread dumpie szukam parkujących rodziców oraz rosnącej kolejki tasków, a nie tylko monitorów w stanie BLOCKED.

## ASYNC-04. Jak CompletableFuture wybiera wątek i executor?

Metody `*Async` bez podanego executora zwykle korzystają z common pool; zwykłe kontynuacje mogą wykonać się na wątku kończącym poprzedni etap lub wywołującym rejestrację. CompletableFuture opisuje wynik i zależności, nie gwarantuje osobnego wątku dla każdej operacji.

`thenApply` mapuje wynik, `thenCompose` spłaszcza następny etap async, a `thenCombine` łączy niezależne wyniki. Blokujące I/O izoluję od wspólnej puli. Określam obsługę wyjątków i zamknięcie executora, zamiast ignorować zwrócony Future.

Zwykła kontynuacja może wykonać kosztowną pracę w wątku, który miał tylko odebrać odpowiedź sieciową. To niespodziewanie opóźnia obsługę innych callbacków. Jawne thenApplyAsync z określonym executorem tworzy granicę, ale dodaje kolejkę i przełączenie.

<!-- example:ASYNC-04-1 kind:java-statements -->
**Kontekst:** Java 21 — instrukcje ciała metody; kompilowane przez skrypt w metodzie pomocniczej.

~~~java
var result = java.util.concurrent.CompletableFuture.completedFuture(10)
    .thenApply(value -> value * 2)
    .thenCompose(value ->
        java.util.concurrent.CompletableFuture.completedFuture("value=" + value));
~~~

Przykład pokazuje mapowanie i spłaszczenie, bez gwarancji nowego wątku. Do konwersji błędu używam handle/exceptionally według kontraktu; whenComplete nadaje się do obserwacji, ale wyjątek z samego callbacka także wpływa na pipeline. [Kontrakt CompletableFuture](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/CompletableFuture.html).

## ASYNC-05. Jak przyspieszyć API wykonujące trzy niezależne wywołania downstream?

Najpierw potwierdzam niezależność: równoległe wywołania skracają ścieżkę z sumy czasów do zbliżonego maksimum, powiększając jednocześnie obciążenie. Stosuję bounded fan-out, wspólny deadline oraz osobne limity zależności. Nie utrzymuję otwartej transakcji DB przez czas HTTP.

Łączę wyniki przez Future/CompletableFuture lub API wspierające zarządzanie podzadaniami w używanej wersji JDK. Określam, czy brak jednej odpowiedzi przerywa całość, czy zwracam jawny wynik częściowy. Nie maskuję braku ceny losową wartością domyślną.

Jeżeli trzy wywołania trwają po 10 s, sekwencja kosztuje około 30 s, a równoległe wykonanie około 10 s plus narzut, o ile downstream nie został przeciążony. Ruch do trzech zależności nadal wynosi trzy operacje na request; zwiększenie concurrency może pogorszyć ich czas.

Przed fan-out wyznaczam wspólny deadline i minimalny potrzebny zestaw danych. Gdy odpowiedź cenowa zależy od wyniku dostępności, tych wywołań nie wolno uruchomić bezwarunkowo równolegle. Testuję szybki błąd jednej gałęzi oraz wolną drugą, żeby sprawdzić, czy zbędna praca rzeczywiście kończy się po decyzji klienta.

## ASYNC-06. Jak obsłużyć timeout, anulowanie i częściową awarię zadań?

Deadline obejmuje oczekiwanie w kolejce, pobranie połączenia i I/O. Po jego upływie kończę odpowiedź i próbuję zatrzymać zbędną pracę przez mechanizm danego klienta. `Future.cancel(true)` jedynie żąda przerwania; kod musi reagować i sprzątać zasoby. InterruptedException propaguję albo odtwarzam flagę przerwania, gdy kontrakt wymaga jej zachowania.

Timeout CompletableFuture nie gwarantuje przerwania pierwotnej operacji; jego `cancel` także nie jest uniwersalnym interruptem. Zewnętrzny efekt mógł już zajść, więc retry wymaga idempotencji lub sprawdzenia statusu. Dla częściowego wyniku ustalam kontrakt błędów każdego komponentu.

Rozdzielam trzy momenty: klient przestał czekać, task otrzymał sygnał anulowania, downstream przestał wykonywać operację. Mogą wystąpić w różnym czasie. Przerwanie JDBC lub HTTP zależy od klienta/sterownika i nie dowodzi cofnięcia już zatwierdzonego efektu.

<!-- example:ASYNC-06-1 kind:java-statements -->
**Kontekst:** Java 21 — instrukcje ciała metody; kompilowane przez skrypt w metodzie pomocniczej.

~~~java
var result = new java.util.concurrent.CompletableFuture<String>();
result.orTimeout(200, java.util.concurrent.TimeUnit.MILLISECONDS);
// Timeout kończy Future, ale nie jest kodem anulującym zewnętrzny request.
~~~

Pomiar obejmuje liczbę operacji nadal aktywnych po timeoutach. Jeśli rośnie, aplikacja może wyglądać na szybko odmawiającą, a jednocześnie zatapiać zależność osieroconymi zadaniami. Sprzątanie jest częścią kontraktu, nie dodatkiem do catch.

<!-- follow-up:ASYNC-06 -->
**Sprawdź rozumienie:** Future zakończył się timeoutem, ale liczba aktywnych wywołań HTTP nadal rośnie. Co zostało anulowane?

<details>
<summary>Kierunek odpowiedzi</summary>

Zakończono oczekiwanie na wynik. Nie dowodzi to anulowania requestu ani cofnięcia efektu. Potrzebny jest mechanizm klienta HTTP, sprzątanie oraz idempotencja/status wyniku nieznanego.

</details>

## ASYNC-07. Jak virtual threads działają i kiedy wybrać je zamiast puli platform threads?

JVM przypisuje wiele virtual threads do mniejszej liczby carrierów. Przy obsługiwanym blokującym I/O virtual thread może się odmontować, oddając carrier innym zadaniom. Pozwala to zachować prosty styl blokujący przy dużej liczbie oczekujących operacji.

Nie przyspiesza obliczeń CPU i nie zwiększa pojemności DB, socketów ani zewnętrznego API. Dla pracy CPU ograniczam równoległość do możliwości maszyny. Nie pooluję virtual threads: tworzę wątek na zadanie, a zasoby ograniczam osobno. [JEP 444](https://openjdk.org/jeps/444).

Virtual thread ma własną tożsamość i stos logiczny, chociaż podczas oczekiwania nie musi zajmować carriera. Scheduler wykonuje gotowe fragmenty na wątkach platformowych. To umożliwia prosty try/catch i lokalne zmienne bez ręcznego rozbijania przepływu na callbacki.

<!-- example:ASYNC-07-1 kind:java-statements -->
**Kontekst:** Java 21 — instrukcje ciała metody; kompilowane przez skrypt w metodzie pomocniczej.

~~~java
try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
    var result = executor.submit(() -> "done");
    System.out.println(result.get());
}
~~~

Przykład wymaga JDK 21+. Zamknięcie executora czeka na zakończenie pracy, więc źle napisany task może opóźnić wyjście z zakresu. W produkcji lifecycle executora i deadline są planowane na poziomie aplikacji, a nie przypadkowo per pomocnicza metoda.

## ASYNC-08. Dlaczego po migracji do virtual threads throughput rośnie, a p99 się pogarsza?

Usunięcie tradycyjnej puli może usunąć przypadkowy limit dopuszczanej pracy. Więcej requestów dociera do tej samej DB, która nie dostała dodatkowej przepustowości. Throughput rośnie dzięki pełniejszemu wykorzystaniu zasobu, ale żądania w ogonie kolejki czekają coraz dłużej, podnosząc p99.

Przykładowo DB zdolna stabilnie obsłużyć 300 operacji/s nie skorzysta z 10 000 jednoczesnych tasków tak samo jak z 100. Nadmiar utrzymuje payloady i kontekst w pamięci. W stabilnym oknie zależność L≈λ×W łączy średnią liczbę operacji w toku, napływ i czas; nie jest dowodem stabilności przeciążonej kolejki.

Porównuję pełną dystrybucję, acquisition time, wiek kolejki i odsetek operacji kończących się w deadline przy takim samym modelu obciążenia. Uwzględniam odmowy i timeouty, które mogą wypaść ze statystyk samych sukcesów. Naprawą jest limit współbieżności/admission i load shedding dobrane do downstreamu; dodatkowe carriery nie usuną nasycenia DB.

## ASYNC-09. Co się dzieje, gdy tysiące virtual threads wyczerpią pulę DB?

Czekają na połączenie albo kończą się acquisition timeoutem. Wątek może być tani, ale jego request, bufor i kontekst zajmują pamięć. Zbyt wiele czekających operacji może przeciążyć aplikację przed osiągnięciem limitu samych wątków.

Ograniczam liczbę dopuszczonych operacji DB, skracam transakcje i mierzę czas pożyczania oraz użycia połączeń. Sumuję pule wszystkich instancji. Rozmiar dobieram testem do wydajności bazy, a nie do liczby klientów.

Czas requestu DB składa się z acquisition, pracy na połączeniu i pozostałej części aplikacji. Virtual threads mogą zmniejszyć koszt czekania na acquisition dla JVM, ale nie skracają samej kolejki. Zbyt długi timeout pozwala zgromadzić wiele przeterminowanych żądań.

Ustalam limit aktywnych operacji i maksymalny czas oczekiwania przed wejściem do transakcji. Przy autoscalingu liczę np. 20 podów × 30 połączeń, nie jedynie konfigurację pojedynczego Hikari. REQUIRES_NEW może potrzebować kolejnego połączenia przy już trzymanym zewnętrznym, co dodatkowo zmienia wymagany zapas.

## ASYNC-10. Jak diagnozować pinning w Javie 21 i dlaczego wersja JDK ma znaczenie?

W Javie 21 blokujące działanie wewnątrz `synchronized` może przypiąć virtual thread do carriera; istotny bywa też kod natywny. Korzystam z JFR, zdarzeń pinning, `jdk.tracePinnedThreads` i dumpu obejmującego virtual threads. Szukam długiego I/O pod monitorem, nie samej obecności słowa synchronized.

JEP 491 w JDK 24 usunął pinning wynikający z monitorów synchronized. Nie przenoszę więc diagnozy z 21 bez sprawdzenia wersji. Zmiana na ReentrantLock nie usuwa logicznego contention. [JEP 491](https://openjdk.org/jeps/491).

W JDK 21 kod pobierający lock, a potem czekający na HTTP może zatrzymać carriera na długo. Ślad pinning wskazuje miejsce parkowania, ale przyczyną architektoniczną jest często zbyt szeroka sekcja krytyczna. Sam mechaniczny wybór innego locka zachowa kolejkę na tym samym zasobie.

<!-- example:ASYNC-10-1 kind:shell-command -->
**Kontekst:** Przykładowe polecenia — PID, ścieżki i środowisko należy dopasować; skrypt ich nie wykonuje.

~~~shell
jcmd 1234 JFR.start name=pinning settings=profile duration=60s filename=pinning.jfr
jcmd 1234 Thread.dump_to_file -format=json threads.json
~~~

1234 oznacza PID przykładowego procesu. Zbieranie danych planuję z limitem czasu i miejsca. Przy JDK 24+ sprawdzam pozostałe przypadki natywne oraz rzeczywiste eventy danej wersji zamiast stosować bez zmian stare flagi.

## ASYNC-11. Dlaczego CPU nadal jest wysokie przy virtual threads?

Virtual threads zmniejszają koszt oczekiwania, nie koszt serializacji, kompresji, szyfrowania ani obliczeń. Jeśli request zużywa 20 ms CPU, pojedynczy rdzeń wykona orientacyjnie 50 takich operacji/s przed uwzględnieniem GC i narzutu. Dodatkowe wątki mogą jedynie powiększyć kolejkę gotowych obliczeń.

Profil CPU pokazuje, czy zasób zajmuje logika biznesowa, GC, logowanie, busy waiting czy retry. Zestawiam go z allocation profile, runnable tasks i throttlingiem kontenera: niski CPU całego hosta nie wyklucza wyczerpania przydziału konkretnej usługi.

Ciężką pracę CPU ograniczam osobną pulą lub limitem, aby nie monopolizowała zasobów wspólnych. Wynik oceniam przez latency, ukończony throughput i koszt CPU na operację. Zmniejszenie liczby zbędnych prób lub alokacji może pomóc bardziej niż zmiana schedulera.

## ASYNC-12. Jak ThreadLocal i stosy virtual threads wpływają na pamięć?

Wiele lekkich wątków nadal zatrzymuje stosy, kontekst, bufory i obiekty żądań. ThreadLocal z dużym obiektem na każdy request może kosztować więcej niż dawniej przy małej puli. Nie jest rozsądne cache'owanie zasobu per virtual thread tylko dlatego, że działało per platform thread.

Mierzę liczbę zadań w toku, retained heap, allocation rate i lifetime requestów. Ograniczam admission, rozmiar buforów i fan-out, sprzątam kontekst. Samo podniesienie Xmx nie rozwiąże nieograniczonej kolejki pracy.

Jeżeli każdy z 50 000 requestów trzyma 100 KiB payloadu, same dane wejściowe to około 4,8 GiB, zanim doliczymy stosy, odpowiedzi i obiekty frameworka. „Lekki wątek” nie oznacza lekkiej operacji. Zmniejszenie kosztu wątku może odsłonić problem nieograniczonego in-flight.

W heap dumpie szukam właścicieli buforów i czasu retencji: czy dane są potrzebne przez całą operację, czy można je strumieniować lub zwolnić wcześniej. ThreadLocal z drogim klientem/serializerem na każdy task bywa szczególnie niekorzystny. Limit request size i concurrency chroni pamięć skuteczniej niż optymalizacja kilku małych obiektów.

## ASYNC-13. Jak obsłużyć 10 000 równoczesnych żądań i nie zalać zewnętrznego API?

Rozdzielam równoczesne połączenia, aktywne żądania i RPS. Dla zależności stosuję semafor/bulkhead, ograniczoną kolejkę, timeout oczekiwania i rate limiter dopasowany do kontraktu operatora. Całość musi mieścić się w budżecie czasu użytkownika.

Lokalne limity mnożą się przez liczbę instancji, więc potrzebna bywa koordynowana quota. Przy skoku ruchu odrzucam nadmiar kontrolowanie i respektuję Retry-After, zamiast tworzyć tysiące zadań czekających bez końca. Autoscaling wymaga zapasu downstreamu.

Wyobraźmy sobie kontrakt operatora: 100 requestów/s oraz maksymalnie 20 jednoczesnych połączeń. Token bucket chroni pierwszą regułę, semafor drugą. Przy opóźnieniu 2 s sam limit 100/s pozwoliłby zgromadzić około 200 operacji w toku.

Decyzję o wpuszczeniu podejmuję przed kosztownymi alokacjami i pobraniem connection. Oczekiwanie na semafor ma własny krótki budżet, a globalna quota uwzględnia podział między repliki. Dla niekrytycznych operacji lepsza może być trwała kolejka z kontrolą wieku; dla synchronicznej odpowiedzi trzeba umieć szybko odmówić.

## ASYNC-14. Jak migrować Spring Boot do virtual threads?

Ustalam wspieraną kombinację JDK, Boota, serwera i sterowników; w obsługiwanych wersjach Boota dostępna jest właściwość `spring.threads.virtual.enabled`. Sprawdzam, które executory rzeczywiście podlegają zmianie, a które są własną konfiguracją.

Mierzę baseline i wykonuję stopniowy rollout z testami spike/soak. Audytuję ThreadLocal, pinning dla użytego JDK, limity HTTP/DB i lifecycle: virtual threads są daemonami. Nie zakładam, że jedna właściwość zmienia każdą bibliotekę i każdego konsumenta Kafka. [Dokumentacja wątków Boota](https://docs.spring.io/spring-boot/reference/features/task-execution-and-scheduling.html).

Migracja wymaga pomiaru całego krytycznego przepływu, np. login → odczyt konta → HTTP operatora, a nie benchmarku samego tworzenia wątków. Przed rolloutem zapisuję limity DB/HTTP, liczbę tasków, pamięć i bazowe percentyle. Po zmianie sprawdzam, czy faktycznie wykonuje się virtual thread w każdym zamierzonym miejscu.

<!-- example:ASYNC-14-1 kind:properties-fragment -->
**Kontekst:** Fragment konfiguracji properties; wymaga zgodnej wersji biblioteki i otoczenia; bez testu uruchomienia.

~~~properties
spring.threads.virtual.enabled=true
~~~

Właściwość działa tylko w wspieranej konfiguracji Boot/JDK i nie zastępuje własnych executorów. Test spike wykryje brak admission, a soak retencję kontekstu. Plan cofnięcia powinien obejmować konfigurację i zasoby, aby powrót do starego modelu nie zastał nieobsługiwalnego backlogu.

## ASYNC-15. Czy virtual threads zastępują WebFlux i Reactive Streams?

Virtual threads ułatwiają imperatywną obsługę blokującego I/O. Reactive Streams definiuje asynchroniczny przepływ z demand/backpressure; to przydatne dla strumieni i całego nieblokującego stosu. CompletableFuture opisuje zwykle pojedynczy wynik i nie daje automatycznie protokołu demand.

Nie przepisuję działającego WebFlux bez problemu do rozwiązania. Porównuję biblioteki, umiejętności zespołu, streaming i pomiary. Mono reprezentuje 0–1 element, Flux 0–N; blokowanie event loop psuje wydajność mimo reaktywnego API.

Mono i Flux są typami reprezentującymi obliczenie/strumień, a subscribe uruchamia odpowiedni przepływ w typowym zimnym publisherze. Wiele subskrypcji może powtórzyć efekt HTTP lub DB; sam zapis reaktywny nie implikuje deduplikacji. Block w event loop zabiera wątek potrzebny do obsługi innych połączeń.

Przy wyborze rozważam cały łańcuch, także sterownik DB, tracing i kompetencje zespołu. MVC z virtual threads może uprościć usługę request/response z JDBC. WebFlux może być wygodniejszy przy bogatych strumieniach i reaktywnych zależnościach. Migracja musi przynieść mierzalny zysk większy niż koszt zmiany modelu i diagnostyki.

## ASYNC-16. Jak backpressure chroni wolnego konsumenta?

Konsument sygnalizuje zapotrzebowanie, a zgodny producent dostosowuje emisję. Działa to tylko na granicach respektujących protokół; nie spowalnia magicznie dowolnego urządzenia czy API. Dla źródła bez kontroli potrzebne jest buforowanie z limitem, dropping, sampling albo odmowa.

W projekcie określam maksymalny bufor, zachowanie po jego zapełnieniu i metrykę odrzuceń. Przeniesienie wszystkiego do nieograniczonej kolejki jest odroczeniem OOM. Anulowanie subskrypcji musi sprzątać zasoby źródła.

Backpressure nie oznacza „nigdy nie buforuj”. Operatorzy mogą żądać elementów z wyprzedzeniem, a granice schedulerów tworzą bufory. Dlatego oglądam również prefetch i limity operatorów, a nie tylko obecność Reactive Streams.

Dla lokalizacji kuriera można zastępować stare nieodebrane pozycje najnowszą. Dla komendy przelewu taki dropping jest niedopuszczalny i potrzebna jest trwała kolejka albo odmowa przyjęcia. Semantyka danych wyznacza strategię overflow. W testach szybki producent i celowo spowolniony konsument pokazują wzrost bufora, odrzucenia i poprawne zakończenie po cancel.

## ASYNC-17. Jak przetwarzać rekordy Kafka z virtual threads?

KafkaConsumer nie jest dowolnie thread-safe: poll i zarządzanie przypisaniem pozostają kontrolowane. Można delegować pracę do wątków, ale trzeba ograniczyć in-flight, zachować wymagany porządek per partycja i commitować tylko ciągły zakres ukończonych offsetów.

Przy rebalance zatrzymuję przyjmowanie zadań, kończę lub bezpiecznie porzucam pracę i dopuszczam redelivery. Przekazanie rekordów do nieograniczonej liczby virtual threads może zepsuć kolejność oraz zalać DB. Zaczynam od wspieranej konfiguracji listener containers i pomiaru realnego bottlenecku.

Jeśli rekordy offset 10 i 11 wykonują się równolegle, a 11 kończy pierwszy, commit pozycji po 11 może zgubić 10 po crashu. Potrzebny jest tracker ciągłego prefiksu zakończonych rekordów per partycja, nie „największy zakończony numer”.

W czasie rebalance nowy właściciel może dostać rekord, który stary nadal wykonuje. Idempotentny zapis i fencing ownershipu chronią efekt; przeniesienie do virtual thread niczego tu automatycznie nie zmienia. Przy dużym fan-out stosuję pause/resume lub wspierany mechanizm kontenera oraz ograniczam in-flight według DB i wymaganego porządku.

<!-- follow-up:ASYNC-17 -->
**Sprawdź rozumienie:** Rekord 11 skończył się przed 10. Jaki offset wolno zatwierdzić, jeśli następna nieukończona pozycja to 10?

<details>
<summary>Kierunek odpowiedzi</summary>

Nadal 10, czyli pozycję następnego rekordu. Dopiero ukończenie ciągłego prefiksu pozwala przesunąć commit na 12. Tracker jest osobny per partycja i musi współpracować z rebalance.

</details>

<!-- labs:start -->
## Powiązane laboratoria

Poniższe materiały rozwijają wybrane mechanizmy rozdziału. Każde laboratorium ma własne założenia, ograniczenia i komendy weryfikacji.

- [virtual threads](../backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_a/virtual_threads/README.md)
- [completable future](../backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_a/completable_future/README.md)
- [cancel](../backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_a/cancel/README.md)
- [Reactive Streams — demand i backpressure](../backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_a/reactive_streams/README.md)
<!-- labs:end -->
