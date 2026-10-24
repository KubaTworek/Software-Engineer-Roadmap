# JVM i diagnostyka

[Spis rozdziałów](README.md)

<!-- question-toc:start -->
## Spis pytań

- [JVM-01. Jakie obszary pamięci ma JVM?](#jvm-01-jakie-obszary-pamięci-ma-jvm)
- [JVM-02. Jak ładowana jest klasa i jakie są classloadery?](#jvm-02-jak-ładowana-jest-klasa-i-jakie-są-classloadery)
- [JVM-03. Kiedy obiekt kwalifikuje się do GC i jak działa garbage collection?](#jvm-03-kiedy-obiekt-kwalifikuje-się-do-gc-i-jak-działa-garbage-collection)
- [JVM-04. Co sprawdzić najpierw po OutOfMemoryError, także po miesiącu bez zmian kodu?](#jvm-04-co-sprawdzić-najpierw-po-outofmemoryerror-także-po-miesiącu-bez-zmian-kodu)
- [JVM-05. Jak potwierdzić wyciek pamięci zamiast zgadywać po wysokim heap usage?](#jvm-05-jak-potwierdzić-wyciek-pamięci-zamiast-zgadywać-po-wysokim-heap-usage)
- [JVM-06. Jak badać częste długie pauzy GC?](#jvm-06-jak-badać-częste-długie-pauzy-gc)
- [JVM-07. Kiedy G1, a kiedy ZGC? Co zmieniło się między Javą 8 i 17?](#jvm-07-kiedy-g1-a-kiedy-zgc-co-zmieniło-się-między-javą-8-i-17)
- [JVM-08. Jak badać wysokie CPU przy małym ruchu?](#jvm-08-jak-badać-wysokie-cpu-przy-małym-ruchu)
- [JVM-09. Co oznaczają tysiące wątków, BLOCKED i lock contention?](#jvm-09-co-oznaczają-tysiące-wątków-blocked-i-lock-contention)
- [JVM-10. Do czego służy Java Flight Recorder i jakich dowodów używać przy losowym zawieszaniu/crashu?](#jvm-10-do-czego-służy-java-flight-recorder-i-jakich-dowodów-używać-przy-losowym-zawieszaniucrashu)
<!-- question-toc:end -->

## JVM-01. Jakie obszary pamięci ma JVM?

Heap przechowuje obiekty zarządzane przez GC. Wątki mają stosy ramek i licznik wykonania; specyfikacja opisuje także method area, a HotSpot używa m.in. Metaspace na metadane klas. Dochodzą code cache, bufory direct i pamięć natywna.

Xmx ogranicza heap, nie całe RSS procesu. Kontener potrzebuje zapasu na pozostałe obszary. Nie zakładam, że każdy obiekt z kodu źródłowego zostanie faktycznie zaalokowany: JIT może wyeliminować alokację przez escape analysis i scalar replacement.

Dla kontenera z limitem 2 GiB ustawienie Xmx=2g pozostawia za mało miejsca na Metaspace, stosy platform threads, kod JIT, bufory i biblioteki natywne. Możliwy jest OOMKill mimo heapu, który nie wygląda na pełny. RSS i użyty heap odpowiadają na różne pytania.

<!-- example:JVM-01-1 kind:shell-command -->
**Kontekst:** Przykładowe polecenia — PID, ścieżki i środowisko należy dopasować; skrypt ich nie wykonuje.

~~~shell
jcmd 1234 GC.heap_info
jcmd 1234 VM.native_memory summary
~~~

Drugie polecenie wymaga uprzednio włączonego Native Memory Tracking i nie obejmuje dowolnej pamięci każdej biblioteki. „Method area” jest pojęciem specyfikacji, więc nie utożsamiam jej bezwarunkowo z jednym fizycznym obszarem każdej JVM.

## JVM-02. Jak ładowana jest klasa i jakie są classloadery?

W uproszczeniu: loading, linking z weryfikacją/przygotowaniem/rozwiązywaniem odwołań oraz initialization z wykonaniem inicjalizatorów statycznych. Typowa hierarchia obejmuje bootstrap, platform i application classloader; w Javie 8 używano nazwy extension loader zamiast platform.

Tożsamość typu zależy od nazwy i definiującego classloadera. Dwie klasy o tej samej nazwie mogą być niezgodne. Przy problemach sprawdzam classpath, moduły, wersję bytecode i wycieki classloaderów; statyczna inicjalizacja nie powinna blokować na długim zewnętrznym I/O.

Typowy parent delegation najpierw pyta nadrzędny loader, ale serwery aplikacyjne/pluginy mogą mieć inne polityki. To pomaga zrozumieć sytuację, gdy biblioteka jest obecna na dysku, a aplikacja jej nie widzi albo dostaje ClassCastException między pozornie identycznymi nazwami.

ClassNotFoundException zwykle wiąże się z jawnym żądaniem ładowania, a NoClassDefFoundError może dotyczyć brakującej zależności lub wcześniejszej nieudanej inicjalizacji. Szukam pierwotnej przyczyny i definiującego loadera. Dynamiczne pluginy wymagają wyrejestrowania listenerów, wątków i ThreadLocal, aby stary classloader przestał być osiągalny po wymianie.

## JVM-03. Kiedy obiekt kwalifikuje się do GC i jak działa garbage collection?

Istotna jest osiągalność od GC roots, np. aktywnych stosów i statycznych referencji, z uwzględnieniem typów referencji. Cykl obiektów bez drogi od roots może być zebrany; samo wyjście zmiennej z widocznego bloku nie jest harmonogramem natychmiastowego zwolnienia.

Kolektor identyfikuje żywe obiekty i odzyskuje pamięć, czasem ją kompaktując lub przenosząc. Nie zamyka deterministycznie połączeń i plików. Dla zasobów stosuję jawne close, zamiast czekać na GC lub finalizację.

Obiekt A wskazuje B, a B wskazuje A. Jeśli żaden root nie prowadzi do tego cyklu, tracing GC może odzyskać oba; nie potrzebuje zerowych liczników referencji. Obiekt w statycznej mapie pozostaje natomiast żywy, nawet gdy „aplikacja już go nie używa” według intencji autora.

Słabe i miękkie referencje zmieniają zasady utrzymywania osiągalności, ale nie są precyzyjną polityką cache. SoftReference nie gwarantuje oczekiwanego hit ratio i stabilnej latency. Garbage collection zarządza pamięcią, a nie poprawnością business lifecycle: niezarejestrowany job czy niezamknięty connection nadal wymagają osobnego mechanizmu sprzątania.

## JVM-04. Co sprawdzić najpierw po OutOfMemoryError, także po miesiącu bez zmian kodu?

Zachowuję dokładny komunikat i dowody: logi, metryki, heap dump jeśli możliwy, limity kontenera. Rozróżniam heap, Metaspace, direct memory, brak native thread oraz OOMKill przez system. Proces może zostać zabity bez Java exception i heap dumpu.

Patrzę na rosnący live set, kolejki, cache, ThreadLocal, połączenia, nowe klasy i zmianę danych/ruchu. Brak deploya nie wyklucza problemu zależnego od czasu. Dump może zawierać sekrety i wymaga miejsca; najpierw ograniczam wpływ incydentu, potem analizuję dominatory.

Najpierw rozstrzygam, czy proces żyje i czy wolno bezpiecznie zbierać cięższy artefakt. Wzrost liczby restartów i exit code OOMKill kieruje do limitu kontenera; komunikat unable to create native thread do limitów systemowych/pamięci natywnej; Java heap space do retencji i alokacji heap.

<!-- example:JVM-04-1 kind:shell-command -->
**Kontekst:** Przykładowe polecenia — PID, ścieżki i środowisko należy dopasować; skrypt ich nie wykonuje.

~~~shell
jcmd 1234 GC.heap_dump /diagnostics/heap.hprof
~~~

Ścieżka jest przykładowa i musi istnieć z dostatecznym miejscem. Dump może być kosztowny, zawierać dane klientów i wymagać restartu analizy na kopii. Nie wykonuję go bezrefleksyjnie na każdej replice naraz. Zabezpieczenie dostępności oraz zachowanie dowodów to dwa równoległe cele incydentu.

<!-- follow-up:JVM-04 -->
**Sprawdź rozumienie:** Kontener został OOMKilled, ale heap nie doszedł do Xmx. Gdzie szukać pamięci?

<details>
<summary>Kierunek odpowiedzi</summary>

Poza heapem: stosy platform threads, direct buffers, Metaspace, code cache i biblioteki natywne. Sprawdź limit kontenera, RSS, exit code i dostępne dane NMT; Xmx nie ogranicza całego procesu.

</details>

## JVM-05. Jak potwierdzić wyciek pamięci zamiast zgadywać po wysokim heap usage?

Porównuję live set po cyklach GC przy porównywalnym obciążeniu, retained heap i ścieżki do GC roots. Chwilowe zużycie heapu lub wysoki allocation rate nie dowodzą wycieku; mogą wynikać z legalnej pracy i krótkotrwałych obiektów.

Typowe przyczyny to cache bez limitu, listener bez wyrejestrowania, zakończone Future w mapie i ThreadLocal. Usuwam przyczynę retencji, a potem sprawdzam trend podczas soak testu. Sama częstsza kolekcja nie zwolni nadal osiągalnych obiektów.

Przykład: po każdym pełnym cyklu live set rośnie 200 → 350 → 500 MiB przy podobnym ruchu. Heap dump pokazuje, że mapę zakończonych Future trzyma singleton, a każda wartość zachowuje odpowiedź HTTP. To wskazuje konkretną ścieżkę retencji, nie „GC nie nadąża”.

Po naprawie porównuję plateau pamięci podczas długiego testu i liczbę wpisów po zakończeniu zadań. Dominator wskazuje, co utrzymuje dużo danych; nie oznacza automatycznie błędu właściciela, bo duży cache może być celowy. O wycieku decyduje kontrakt życia danych i brak osiągania ograniczonego stanu.

[Przeczytaj przykładowy materiał diagnostyczny](DIAGNOSTYKA.md#diag-05-jfr-gc-i-pamięć).

## JVM-06. Jak badać częste długie pauzy GC?

Łączę logi GC z p99, allocation rate, rozmiarem live set, promocją, humongous allocations i ograniczeniami CPU. Ustalam kolektor oraz przyczynę cyklu/pauzy. Czas aplikacji może rosnąć także przez safepointy lub throttling, a nie wyłącznie GC.

Najpierw redukuję niepotrzebne alokacje i retencję oraz dobieram pamięć. Zmianę kolektora oceniam na reprezentatywnym workloadzie. Większy heap może pomóc lub wydłużyć odzyskiwanie; arbitralne zmniejszenie celu pauzy może pogorszyć throughput i nie stanowi gwarancji SLO.

Dwie usługi o takim samym heap usage mogą mieć inny problem: jedna masowo alokuje krótkotrwałe DTO, druga zatrzymuje długowieczne wyniki. W pierwszej pomaga ograniczenie allocation churn, w drugiej retencji. Sama liczba cykli GC nie rozstrzyga, który przypadek wystąpił.

<!-- example:JVM-06-1 kind:shell-command -->
**Kontekst:** Przykładowe polecenia — PID, ścieżki i środowisko należy dopasować; skrypt ich nie wykonuje.

~~~shell
java -Xlog:gc*,safepoint:file=gc.log:time,uptime,level,tags Example
~~~

To przykład uruchomienia procesu, nie polecenie zmieniające dowolną działającą JVM. Analizuję rozmiar przed/po cyklu, czas współbieżnej pracy i pauzy. Krótkie pauzy przy stale zajętym CPU też mogą pogarszać p99, więc nie optymalizuję wyłącznie jednej metryki kolektora.

<!-- follow-up:JVM-06 -->
**Sprawdź rozumienie:** GC jest częsty, ale live set po cyklach pozostaje stały. Czy to dowód wycieku?

<details>
<summary>Kierunek odpowiedzi</summary>

Nie. Może to być allocation churn. Wyciek/nieograniczona retencja wymaga analizy rosnących osiągalnych danych, czasu ich życia i ścieżek do roots. Liczba cykli sama nie wybiera przyczyny.

</details>

## JVM-07. Kiedy G1, a kiedy ZGC? Co zmieniło się między Javą 8 i 17?

G1 jest rozsądnym punktem startu dla wielu usług; ZGC projektowano dla krótkich pauz, z kosztem dodatkowej pracy współbieżnej i wymaganiami zasobowymi. Wybór zależy od live set, SLO, CPU i konkretnego wydania, nie tylko rozmiaru heapu.

Java 8 zastąpiła PermGen Metaspace; domyślny kolektor zależał od platformy. G1 stał się domyślnym w JDK 9, a ZGC produkcyjnym w 15. Nie przypisuję tych zmian do 17 ani nie zakładam identycznych możliwości ZGC we wszystkich wydaniach. [JEP 248](https://openjdk.org/jeps/248), [JEP 377](https://openjdk.org/jeps/377).

Punktem porównania jest ten sam ruch, rozgrzanie i limity CPU/pamięci. Kolektor o krótszych pauzach może potrzebować więcej CPU albo zapasu heapu do pracy współbieżnej. Dla batcha kończącego się w godzinę większy throughput może być ważniejszy niż pojedyncza krótka pauza; dla interaktywnego API odwrotnie.

Sprawdzam też wersję i tryb kolektora: określenie „ZGC” obejmuje ewolucję między wydaniami. Nie przenoszę flag historycznych bez walidacji. Sukces oznacza spełnienie SLO z akceptowalnym kosztem zasobów, a nie sam wybór nowszego algorytmu lub większego Xmx.

## JVM-08. Jak badać wysokie CPU przy małym ruchu?

Ustalam, który proces i wątki zużywają CPU, po czym zbieram profil i kilka dumpów. Szukam pętli, retry bez opóźnienia, spin locków, kosztownej serializacji, regexów, GC i zadań okresowych. Niski ruch HTTP nie oznacza braku pracy w tle.

Porównuję zużycie z limitem kontenera, throttlingiem i liczbą rzeczywiście dostępnych rdzeni. Reaguję na dominujący stos, zamiast zwiększać thread pool. Przy pomiarze samego czasu ściennego odróżniam pracę CPU od czekania.

Seria profili pokazująca ten sam gorący stos pomaga odróżnić stałą pętlę od krótkiego szczytu. Jeśli CPU zużywa wątek zadania okresowego, koreluję początek z harmonogramem i danymi wejściowymi. Jeśli GC, wracam do allocation rate i live set.

<!-- example:JVM-08-1 kind:shell-command -->
**Kontekst:** Przykładowe polecenia — PID, ścieżki i środowisko należy dopasować; skrypt ich nie wykonuje.

~~~shell
jcmd 1234 JFR.start name=cpu settings=profile duration=30s filename=cpu.jfr
jcmd 1234 Thread.print -l
~~~

Polecenia dotyczą przykładowego PID. Stos odczytany raz nie jest jeszcze dowodem, że dana metoda dominuje koszt. Przy CPU „poza Javą” sprawdzam natywne biblioteki i system, a nie wyłącznie nazwy metod aplikacji.

## JVM-09. Co oznaczają tysiące wątków, BLOCKED i lock contention?

Może chodzić o cached pool bez limitu, tworzenie executora na request, niedomknięte pule lub zawieszone I/O. BLOCKED oznacza oczekiwanie na monitor, a WAITING/TIMED_WAITING może być poprawnym parkowaniem. Virtual threads liczę osobno od OS threads.

Seria dumpów i JFR pokażą właściciela monitora, czas blokady i postęp. Skracam sekcje krytyczne, ograniczam współdzielenie i sprawdzam I/O pod blokadą. Dodanie wątków do jednego wąskiego gardła zwykle zwiększa kolejkę.

Contention oznacza rywalizację o ten sam zasób, deadlock brak możliwości postępu przez cykl zależności. Wiele BLOCKED na jednym monitorze może być zwykłym serializowanym hot path z wolnym właścicielem. Istotny jest czas utrzymania locka oraz praca wykonywana w jego wnętrzu.

Przeniesienie I/O poza sekcję wymaga zachowania poprawności: stan mógł się zmienić podczas wywołania, więc czasem potrzebna jest wersja i ponowna walidacja. Dla liczników metryk LongAdder może zmniejszyć contention, lecz jego sum nie jest atomowym snapshotem do egzekwowania salda czy twardego limitu.

[Przeczytaj przykładowy materiał diagnostyczny](DIAGNOSTYKA.md#diag-02-thread-dump-blocked-i-właściciel-monitora).

## JVM-10. Do czego służy Java Flight Recorder i jakich dowodów używać przy losowym zawieszaniu/crashu?

JFR zapisuje zdarzenia runtime, m.in. CPU sampling, alokacje, blokady i I/O, z kosztem zależnym od konfiguracji. Łączę go z metrykami i trace konkretnego incydentu. Dla długich zawieszeń seria thread dumpów rozróżnia brak postępu od normalnego oczekiwania.

Przy crashu sprawdzam exit code, OOMKill, restart count, logi systemowe i `hs_err_pid` dla błędu natywnego. Aplikacja „nadal działa” nie oznacza, że może obsłużyć request. Sondy i watchdog muszą wykrywać realną gotowość, bez restartowania usługi przy każdej awarii zależności.

Przy losowym crashu JVM szukam pliku błędu natywnego i biblioteki ze stosu, a przy SIGKILL/OOMKill logów platformy. Jeśli proces jedynie nie odpowiada, sprawdzam, czy wątki czekają na jedną pulę, lock albo niedostępną sieć. Te przypadki mogą mieć podobny objaw HTTP.

Plan diagnostyki ustalam przed incydentem: gdzie zapisać JFR, kto ma dostęp, ile miejsca i jak ograniczyć czas nagrania. Zapis „restart pomógł” nie rozstrzyga przyczyny. Po przywróceniu ruchu porównuję wiek kolejek i poprawność danych, bo odzyskany port nie jest jeszcze zakończeniem awarii.

<!-- labs:start -->
## Powiązane laboratoria

Poniższe materiały rozwijają wybrane mechanizmy rozdziału. Każde laboratorium ma własne założenia, ograniczenia i komendy weryfikacji.

- [allocation rate](../backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_b/allocation_rate/README.md)
- [lock contention](../backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_b/lock_contention/README.md)
- [g1 vs zgc](../backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_b/g1_vs_zgc/README.md)
<!-- labs:end -->
