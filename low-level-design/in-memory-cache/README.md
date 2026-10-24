# Cache w pamięci

Projekt edukacyjny w czystej Javie: cache o ograniczonej liczbie wpisów, z LRU, LFU i wygasaniem TTL. Implementacja wykorzystuje indeksowany kopiec terminów i kubełki częstotliwości. Stan jest w pamięci, dostęp jednowątkowy.

## Rdzeń techniczny

[InMemoryCache](src/main/java/pl/jakubtworek/lld/cache/domain/InMemoryCache.java) utrzymuje LinkedHashMap w kolejności LRU → MRU, jawnie przenosząc użyty wpis na koniec.

LFU łączy listę dwukierunkową kubełków częstotliwości, LinkedHashSet kluczy w każdym kubełku i mapę klucz → kubełek. Użycie przesuwa wpis tylko do częstotliwości f + 1; pusty kubełek znika bez przeglądania pozostałych.

Kopiec binarny terminów posiada mapę klucz → indeks tablicy. Zmiana lub usunięcie terminu naprawia kopiec i mapę przy każdej zamianie. Każdy wpis z TTL ma dokładnie jeden rekord w kopcu; nie powstają stare bilety oczekujące na sprzątnięcie.

N to liczba wpisów, T wpisów z TTL, E wygasłych wpisów usuwanych przy wywołaniu.

| Operacja | Koszt |
| --- | --- |
| Get / peek bez wygaszania | Średnio O(1) |
| Utrzymanie LRU / LFU, wybór ofiary | Średnio O(1) |
| Dodanie / zmiana / usunięcie terminu | O(log(T + 1)) |
| Usunięcie E wygasłych wpisów | O(E log(T + 1)), doliczane do kosztu wywołania |
| Resize z V ofiarami | Koszt wygaszania + O(V × (1 + log(T + 1))) |
| Snapshot | Koszt wygaszania + O(N) kopii wyniku |

Logiczna pamięć struktur wynosi O(N), ale tablice kolekcji mogą zachować wcześniej przydzieloną pojemność. Clear nie gwarantuje zmniejszenia tej pojemności. Diagnostyka wymaga scheduledRecords = timedEntries; licznik compactions pozostaje zerowy.

### Jak aktualizowany jest kopiec TTL

Mapa położenia wskazuje indeks klucza w tablicy kopca. Podmiana terminu
naprawia porządek przez przesunięcie w górę albo w dół. Usunięcie środka
przenosi ostatni element na zwolnione miejsce, aktualizuje jego indeks i
przywraca porządek. Każda zamiana musi zmienić oba wpisy mapy położenia.

Przykład: A ma termin 5, B termin 10. Przedłużenie A do 20 pozostawia
w kopcu dwa rekordy i umieszcza B przed A. Po czasie 10 usuwane jest B;
stary termin A = 5 nie istnieje już w strukturze. Wielokrotne odnawianie
jednego klucza nie zwiększa liczby rekordów ponad jeden.

Przed usuwaniem wygasłych wpisów put sprawdza dane, konwersję Duration
i możliwość wyliczenia terminu. Dzięki temu niepoprawne żądanie z przepełnieniem
nie sprząta przy okazji innych wpisów. To istotna część kontraktu odmowy.

### Jak działa remis LFU

Kubełki są uporządkowane rosnąco po częstotliwości. W każdym LinkedHashSet
najstarszy wpis jest pierwszy. Użycie klucza usuwa go z obecnego kubełka
i dopisuje na koniec kubełka f + 1, tworzonego obok, gdy go brakuje.
Ofiarą jest pierwszy klucz pierwszego kubełka.

Przy nasyceniu częstotliwości na Long.MAX_VALUE nadal aktualizowany jest
porządek użycia w tym samym kubełku. Bez tego wpisy o równym maksymalnym
liczniku przestałyby respektować regułę remisu LRU.

## Mechanizmy Javy i model wątkowy

### LinkedHashMap: insertion-order zamiast automatycznego access-order

entries powstaje jako zwykła LinkedHashMap w kolejności wstawiania.
Samo put pod istniejący klucz nie przenosi go na koniec; kod robi remove
i put, aby jawnie oznaczyć użycie. Peek może dzięki temu korzystać ze
zwykłego get mapy bez zmiany recency.

Wariant accessOrder=true przestawiałby wpis także przy odczycie get.
Użycie go bez zmiany implementacji peek naruszyłoby kontrakt. Jest to
różnica zachowania konkretnego konstruktora JDK, nie tylko inna nazwa LRU.

W LFU LinkedHashSet zachowuje kolejność kluczy w kubełku. Remove i ponowne
add odświeżają remis LRU, a getFirst wybiera najstarszy element.
HashMap locations wskazuje kubełek, więc nie trzeba szukać go po częstotliwości.

### Kopiec w ArrayList i powód własnego indeksu

IndexedDeadlineHeap przechowuje elementy w ArrayList i pozycje w
`HashMap<K, Integer>`. get/set po indeksie mają O(1), a dopisanie jest
amortyzowane O(1); przesiewanie odwiedza O(log T) pozycji. Integer
w mapie to boxing indeksu, a referencje do rekordów kopca są oddzielne
od tablicy ArrayList.

PriorityQueue daje szybkie poll minimum, ale remove(Object) szuka elementu
liniowo. Ponadto jej iterator nie gwarantuje kolejności od najmniejszego
terminu. Nie zastąpiłaby bezpośrednio indeksowanego usuwania po kluczu
ani uporządkowanego raportowania terminów.

Wewnętrzne kolekcje mogą zatrzymać pojemność po clear. Wartości V i klucze K
są referencjami klienta, nie sklonowanymi obiektami. Mutacja hashCode klucza
po put mogłaby uszkodzić zgodność mapy entries, locations i positions.

### Get również zmienia stan: granica blokady

Get aktualizuje recency, LFU, statystyki i może usunąć wygasłe wpisy.
Peek nie dotyka recency, ale też może wygaszać. Dlatego podział „odczyty
bez blokady, zapisy z blokadą” nie odpowiada rzeczywistym efektom metod.

ConcurrentHashMap nie połączyłaby tych zmian z listą kubełków i kopcem.
Obecny cache wymaga jednego wątku. Ewentualny kontrakt współbieżny musiałby
obejmować całe operacje, nie tylko podmianę mapy; nie ma tu takiej implementacji.
Kontrakty JDK: [LinkedHashMap](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/LinkedHashMap.html)
i [PriorityQueue](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/PriorityQueue.html).

## Zakres, reguły i niezmienniki

Aktor to użytkownik lokalnego API. Cache nie jest źródłem prawdy, nie ma bazy,
sieci ani loadera. Pieniądze nie występują. Jedna instancja działa w jednym wątku.

| ID | Reguła |
| --- | --- |
| R1 | capacity > 0; po operacji fizyczny rozmiar nie przekracza capacity |
| R2 | K/V są nie-null, a equals/hashCode klucza pozostają stabilne |
| R3 | Hit get i put przesuwają wpis na MRU; miss/peek nie dotykają recency |
| R4 | Nowy put ma frequency=1; aktualizacja żywego wpisu i hit get zwiększają frequency |
| R5 | LRU wybiera najstarszy wpis, LFU minimum frequency, remis według LRU |
| R6 | TTL > 0, po konwersji mieści się w long ns; expiry przy tick >= deadline |
| R7 | Get nie odnawia TTL; nowy put ustala nowy deadline albo bezterminowość |
| R8 | Sprzątamy wygasłe przed wyborem ofiary put/resize |
| R9 | Stary Ticket nie usuwa nowej wersji tego samego klucza |
| R10 | Indeks ma dokładnie timedEntries rekordów, nie przechowuje martwych terminów |
| R11 | Operacje czasowe odczytują Ticker raz; Clock czyta tylko snapshot |
| R12 | Błędne dane, błąd źródła czasu lub przepełnienie deadline nie zmieniają wpisów/statystyk/indeksu |
| R13 | Statystyki i diagnostyka nie czytają czasu i nie sprzątają |
| R14 | Resize najpierw wygasza, potem usuwa ofiary; wzrost nie zmienia recency |
| R15 | Clear zachowuje liczniki; resetStatistics zeruje wyłącznie statystyki |
| R16 | Snapshot chroni listę, ale nie klonuje generycznych K/V |

Nie bronimy przed wyjątkami i efektami ubocznymi equals/hashCode własnego klucza.
Zalecamy niemutowalne K/V. Liczniki statystyk, frequency i compactions nasycają
się przy Long.MAX_VALUE. Nie zawijają się na wartości ujemne.

Poza zakresem: współbieżność, źródła danych, odświeżanie w tle, expire-after-access,
limit bajtów, trwałość, serializacja, listenery i przełączanie polityki istniejącej
instancji. Pojemność to liczba wpisów, nie ich waga.

## Reprezentacja stanu

LinkedHashMap pracuje w kolejności wstawiania. Jawny remove+put realizuje
dotknięcie, a zwykły get mapy obsługuje peek bez zmiany kolejności.
EvictionPolicy wskazuje LRU lub LFU; InMemoryCache wybiera głowę recency
lub pierwszy kubełek FrequencyIndex. TTL i statystyki pozostają wspólną ścieżką.
Enum realizuje dwa konkretne warianty bez tworzenia osobnej implementacji cache
z powieloną logiką ważności. Snapshot zawsze pokazuje LRU → MRU, również przy LFU.

## Kontrakty operacji i statystyk

Konstrukcja:
`new InMemoryCache<K,V>(capacity, policy, ticker, clock)`.
Wariant bez policy wybiera LRU. Systemowy czas względny dostarcza
`Ticker.systemTicker()`; do etykiety można podać `Clock.systemUTC()`.

| Operacja | Efekt |
| --- | --- |
| get | Purge, hit/miss, hit zwiększa frequency i przesuwa na MRU |
| peek | Purge, wartość bez dotykania i bez hit/miss |
| put bez TTL | Purge, nowa wersja wpisu, bezterminowość, ewentualna ofiara |
| put z TTL | Walidacja i obliczenie deadline przed purge; aktualizacja frequency tylko żywej wersji |
| invalidate | Purge, usunięcie żywego wskazanego wpisu; invalidations rośnie tylko przy sukcesie |
| clear | Usuwa fizyczne wpisy i indeks, bez czasu; clearRemovals obejmuje także niesprzątane wygasłe |
| size | Purge i liczba żywych wpisów |
| cleanUp | Liczba usuniętych wpisów, których termin upłynął |
| snapshot | Clock, Ticker, purge; widok z policy, datą, tickiem, frequency, remainingTtl i statystykami |
| resize | Dodatnia pojemność, Ticker, purge, ofiary polityki; raport starej/nowej pojemności i obu przyczyn usunięć |
| statistics/diagnostics | Czysty odczyt, bez źródeł czasu i bez sprzątania |
| resetStatistics | Zeruje sześć liczników, bez czasu; nie zmienia wpisów, frequency ani compactions |

[ResizeResult](src/main/java/pl/jakubtworek/lld/cache/domain/ResizeResult.java)
ma expired według kolejności deadline (remisy bez gwarancji) i evicted w kolejności
wyboru polityki. Obie listy zawierają klucz/wartość i są niemutowalne.

CacheStatistics rozdziela hits, misses, expirations, evictions, invalidations
i clearRemovals. Evictions obejmuje put oraz resize. Expirations rośnie także
podczas peek/size/snapshot/resize, jeśli właśnie wtedy wykonano fizyczne usunięcie.
Clear nie wykonuje purge: jeśli usunął zaległy wygasły wpis, zwiększa clearRemovals.
Statystyki nie opisują samego upływu czasu, lecz wykonane operacje.

CacheDiagnostics pokazuje capacity, policy, physicalSize, timedEntries,
scheduledRecords i compactions. physicalSize może zawierać wygasłe wpisy.
Compactions nie podlega resetStatistics; przy jednym rekordzie kopca na wpis
pozostaje zerowe, ponieważ nie trzeba usuwać nagromadzonych starych terminów.

### Kolejność walidacji i błędy

Put z TTL sprawdza nie-null/dodatni TTL i konwersję Duration.toNanos, następnie
K/V, pojedynczy tick i Math.addExact(tick, ttl). Dopiero potem zmienia stan.
Resize sprawdza pojemność przed tickiem. Get/peek/invalidate sprawdzają klucz
przed tickiem. Snapshot odczytuje Clock przed tickiem i purge.

Null daje NullPointerException, błędna pojemność/TTL IllegalArgumentException,
przepełnienie ArithmeticException. Ujemny lub cofnięty tick daje
IllegalStateException. Wyjątek źródła czasu propaguje się bez zmiany cache.
Błąd put nie zatwierdza nawet lastTick. Błąd kodu klucza i wyczerpanie pamięci
są poza gwarancją wycofania.

## Dwa czasy i indeks wygaśnięć

Ticker zwraca nieujemne, niemalejące nanosekundy względem własnego początku.
Systemowy adapter odejmuje początkowy nanoTime. Zakres to Long.MAX_VALUE ns,
około 292 lata od utworzenia adaptera; większy tick lub suma deadline wymagają
nowego kontraktu. Nie utożsamiamy tych liczb z datami UTC.

Clock dostarcza wyłącznie observedAt. Skok Clock nawet do Instant.MIN/MAX
nie zmienia TTL. Cache odrzuca cofnięcie Ticker względem ostatniej udanej
operacji czasowej. Clear nie zeruje lastTick, a nowa instancja zaczyna własne
sprawdzanie od 0. Clock nie jest czytany przy get/put/resize.

IndexedDeadlineHeap przechowuje dokładnie jeden Ticket(deadline,key,identity)
na wpis z TTL. Mapa klucz→pozycja umożliwia fizyczne usunięcie starego terminu
w O(log T). Purge pobiera tylko minima z deadline≤now; nie ma martwych biletów.

## Prześledź LFU, wygaśnięcie i resize

Pojemność 3, polityka LFU, tick=0:

| Działanie | Recency LRU → MRU | Frequency / efekt |
| --- | --- | --- |
| put(a,A) | a | a=1 |
| get(a), get(a) | a | a=3, hits=2 |
| put(b,B) | a,b | b=1 |
| put(t,T,1 s) | a,b,t | t=1 |
| Upływ 1 s | fizycznie a,b,t | Diagnostyka nadal widzi 3 |
| resize(1) | a | expired=[t], evicted=[b] |
| get(a) | a | a=4, hits=3 |

Dla LRU ten sam resize usunąłby a, pozostawiając b. Częstotliwość i recency
to różne informacje, a TTL rozstrzygamy przed wyborem ofiary.
Peek b nie zwiększyłoby jego frequency i nie zmieniło wyniku LFU.

## Uruchomienie

Java 21; z katalogu low-level-design/in-memory-cache:

~~~powershell
.\mvnw.cmd --batch-mode --no-transfer-progress clean verify
& "$env:JAVA_HOME/bin/java.exe" -jar target/in-memory-cache-1.0.0-SNAPSHOT.jar
~~~

Demo zaczyna od pojemności 3 i LRU. Polecenia: put, put-ttl, get, peek, remove, show, size, clean, clear, resize, stats, diagnostics i reset-stats. `advance SECONDS` przesuwa Ticker, `wall SECONDS` zegar etykiety. policy ustawia wybór dla następnej instancji tworzonej przez new. help podaje argumenty, q kończy demo.

## Testy i weryfikacja

[Testy](src/test/java/pl/jakubtworek/lld/cache) porównują kopiec i LFU z modelami skanującymi, a 40000 operacji cache z niezależnym modelem historii. Obejmują dokładną granicę TTL, aktualizacje terminów, resize, odmowy bez zmian, statystyki oraz zgodność rozmiaru indeksów.

| Reguła | Test | Granica |
| --- | --- | --- |
| R1–R5 | fullScenarioEvictsLeastRecentlyUsedAfterHit, policiesDifferAndPeekNeverRaisesFrequency, lfuTieUsesLruAndUpdateCountsAsUseButReplacementAfterExpiryResetsFrequency | Obie polityki, remis i aktualizacja |
| R6–R8 | ttlBoundaryIsInclusiveAtDeadline, expiredMruIsRemovedBeforeEvictingLiveLru, wallClockJumpsDoNotChangeTtl | Nanosekunda przed/na/po, niezależność Clock |
| R9/R10 | staleTicketsCannotExpireUpdatedOrRecreatedKey, sameDeadlineDifferentVersionsStillExpireOnce, indexIsBoundedUnderThousandsOfUpdatesAndShrinksAfterRemoval | 3000 aktualizacji, stare wersje i dokładnie jeden rekord na TTL |
| R11/R12 | everyTimedOperationReadsTickerOnceAndOnlySnapshotReadsClock, onlySnapshotReadsClockAndFailuresDoNotChangeAnything, tickerFailureRollbackAndOverflowHaveNoStateOrStatsEffects | Odczyty źródeł, cofnięcie i błędy zakresu |
| R13–R15 | countersDistinguishReasonsAndClearDoesNotResetThem, diagnosticsAndStatsDoNotPurgeOrReadTimeAndInvalidCommandsDoNotChangeThem, resizeExpiresFirstThenReportsVictimsInPolicyOrderAndGrowthPreservesOrder | Powody usunięcia, czyste odczyty, shrink/grow |
| R16 | snapshotIsHistoricalAndShowsRemainingTtlNotWallClockDeadline, genericValuesAreSharedReferencesNotDeepCopies | Stabilna lista i granica generycznych referencji |
| Model | mixedHistoriesAgreeWithoutImplicitSnapshotCleanup | Po 100 historii × 200 kroków dla LRU i LFU |
| Adapter | lfuResizeStatisticsAndWallClockAreObservable, invalidPolicyAndOverflowAdvancePreserveConfigurationAndTick | Konsola, wybór polityki, data i przepełnienie |

## Ograniczenia

Brak współbieżności, automatycznego ładowania wartości, zadań w tle i trwałości. Limit dotyczy liczby wpisów, nie liczby bajtów; pamięć obiektów wartości nie jest kontrolowana.
