# Biblioteka logowania

Projekt edukacyjny w czystej Javie: filtrowanie poziomów, formatowanie TEXT/JSON, maskowanie kontekstu i zapis do wielu odbiorców. Implementacja obejmuje bufor pierścieniowy i raport wyniku dla każdego odbiorcy. Wywołania są synchroniczne i jednowątkowe.

## Rdzeń techniczny

[Logger](src/main/java/pl/jakubtworek/lld/logging/domain/Logger.java) kompiluje tablicę tras poziom → BitSet odbiorców. Dla całkowitego odfiltrowania ma gotowy niemutowalny wynik.

MemoryLogSink używa tablicy String o stałej pojemności C oraz indeksu początku i licznika zajętości. DROP_OLDEST nadpisuje najstarszy rekord i zwiększa licznik odrzuconych starych wpisów. REJECT_NEW zachowuje zawartość, zwiększa licznik odmów i zgłasza SinkCapacityException.

TEXT i JSON uciekają cudzysłowy, ukośniki odwrotne, CR/LF, tabulatory oraz znaki sterujące i DEL. Tablica cyfr szesnastkowych zastępuje formatowanie pojedynczych znaków. Kontekst JSON jest oddzielnym obiektem; nie nadpisuje pól zdarzenia.

T oznacza liczbę odbiorców, F pól kontekstu, M długość rekordu, H liczbę wpisów bufora.

| Operacja | Koszt |
| --- | --- |
| Całkowite odfiltrowanie | O(1) dla gotowej konfiguracji |
| Przygotowanie kontekstu | O(F log F) oraz koszt jego danych |
| Routing i pełny raport | O(T), niezależnie od liczby przyjętych zapisów |
| Formatowanie | O(M); zapis do T odbiorców może kosztować O(T × M) i dodatkowo czas I/O |
| Dopisanie do bufora | O(1), O(C) zarezerwowanej pamięci |
| Odczyt bufora / clear | O(H) kopii wyniku / O(C) czyszczenia tablicy |

Odczyt bufora zwraca niezależną listę; clear nie zeruje liczników. Zewnętrzne kopie rekordów mogą utrzymywać pamięć po usunięciu ich z bufora. WriterLogSink dopisuje LF i wykonuje flush, ale nie zamyka pożyczonego Writer.

### Przygotowanie rekordu i nieodwracalne zapisy

Jedno wywołanie tworzy jedno zdarzenie i jeden tekst rekordu, nawet jeżeli
trafia do kilku odbiorców. Supplier nie może być uruchamiany ponownie dla
każdego z nich: mógłby być kosztowny albo zwracać różne wiadomości.

Maskowanie odbywa się przed formatowaniem i zbudowaniem wyniku widocznego
dla wywołującego. Zamaskowanie wyłącznie tekstu JSON pozostawiłoby surową
wartość w LogResult.event. Dane wejściowego kontekstu nie są przy tym zmieniane.

Przy częściowej awarii wcześniejszego zapisu nie można wycofać. CONTINUE
daje szansę dalszym odbiorcom, STOP_ON_FAILURE oznacza brak próby ich obsługi.
Żaden wariant nie gwarantuje dostarczenia wszędzie dokładnie raz. Automatyczne
ponowienie mogłoby zdublować rekord u odbiorcy, który zapisał go przed awarią.

### Granice bufora

Wypełniona tablica nie przesuwa wszystkich rekordów. DROP_OLDEST zmienia
początek pierścienia i nadpisuje jedną komórkę; REJECT_NEW zachowuje dotychczasową
kolejność i odmawia. W obu przypadkach koszt dopisania nie zależy od pojemności.

Limit C ogranicza liczbę referencji w buforze, nie rozmiar tekstu ani listy
zachowanej przez czytelnika. records() tworzy własny wynik, więc późniejsze
clear nie opróżnia wcześniej pobranej listy. Wyzerowanie komórek usuwa
referencje bufora, ale nie te utrzymywane na zewnątrz.

## Mechanizmy Javy i model wątkowy

### EnumMap, BitSet i leniwe wykonanie Supplier

routes jest EnumMap po LogLevel. Każda wartość BitSet wskazuje indeksy
odbiorców w niezmiennej liście targets. Maski powstają w konstruktorze;
dispatch odczytuje ich bity i nie wykonuje na nich mutującego and.
Pusta maska pozwala zwrócić gotowy filtered bez tworzenia zdarzenia.

`Supplier<String>` przesuwa wywołanie get do chwili po filtrowaniu. Nie cofa
jednak pracy wykonanej przy konstruowaniu argumentu. Wariant
log(level, expensiveMessage()) obliczy tekst przed wejściem do metody,
a log(level, () -> expensiveMessage()) pozwala tej pracy uniknąć.
Samo utworzenie lambdy nie jest obietnicą braku alokacji na każdej JVM.

Raport używa `ArrayList<Delivery>`, bo potrzebuje jednego wyniku na trasę.
Jedno formatowanie nie usuwa O(T) pracy budowania raportu. Lista targets
jest kopiowana, ale LogSink pozostaje współdzielonym obiektem ze swoim stanem.

### String[] jako pierścień i własność Writer

MemoryLogSink rezerwuje String[C] i przechowuje referencje do tekstów.
Zmiana head/size oraz jednej komórki zastępuje przesuwanie C elementów.
clear wywołuje Arrays.fill(records, null), aby usunąć referencje bufora;
sam size = 0 pozostawiłby teksty osiągalne w tablicy.

records() tworzy wynik w kolejności logicznej, niezależnej od fizycznego
zawinięcia tablicy. String jest niemutowalny, więc kopiowanie referencji
wystarcza do stabilnego widoku. Nie byłoby to wystarczające dla mutowalnych
buforów znaków wystawionych bez kopii.

Writer jest zasobem przekazanym z zewnątrz. Sink wykonuje write i flush,
ale nie close. Automatyczny try-with-resources zamknąłby także pożyczony
strumień, co wykraczałoby poza własność tej klasy.

### Reentrancja, monitor i blokujące I/O

active to zwykłe boolean. Check i przypisanie nie są operacją atomową
między wątkami. Flaga wykrywa zagnieżdżony log wywołany np. przez Supplier
w tym samym przepływie. finally czyści ją również po wyjątku; nie daje
to bezpieczeństwa równoległym wywołaniom.

Nawet synchronized jest w Javie reentrant: ten sam wątek może ponownie
wejść do monitora. Nie zastąpiłby więc sam zakazu zagnieżdżonego logowania.
Z kolei współdzielony sink wymagałby uzgodnienia dostępu także między
różnymi loggerami. Obecna konfiguracja działa w jednym wątku.

Zapis do Writer jest synchroniczny i może blokować. Supplier nie czyni
loggera asynchronicznym, a brak Thread.sleep nie oznacza nieblokującego I/O.
Źródła kontraktów: [BitSet](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/BitSet.html)
i [monitory Javy](https://docs.oracle.com/javase/specs/jls/se21/html/jls-17.html#jls-17.1).

## Zakres, reguły i niezmienniki

Aktor to kod aplikacji. Clock dostarcza datę zdarzenia; może się cofać i nie
wyznacza kolejności. Pieniądze nie występują. Jedna instancja wraz z sinkami
jest obsługiwana w jednym wątku.

| ID | Reguła |
| --- | --- |
| R1 | TRACE < DEBUG < INFO < WARN < ERROR; progi loggera i targetu działają przez AND |
| R2 | Brak dopuszczonego targetu pomija Supplier, masker, Clock i formatter |
| R3 | Dopuszczony log przygotowuje komunikat, maskowanie, czas i format raz, przed sinkami |
| R4 | Targety są niepuste, unikalnie nazwane, kopiowane i wykonywane w kolejności |
| R5 | CONTINUE próbuje następnych tras po IOException/RuntimeException zapisu |
| R6 | STOP_ON_FAILURE pomija dalsze dopuszczone trasy, nie cofa wcześniejszych zapisów |
| R7 | Raport rozróżnia FILTERED, WRITTEN, FAILED i NOT_ATTEMPTED |
| R8 | Błąd przygotowania propaguje się bez prób zapisu; Error nie jest połykany |
| R9 | Ponowne wejście w aktywny logger jest odrzucane przed filtrowaniem |
| R10 | Flaga aktywności jest zwalniana także po wyjątku lub Error |
| R11 | Kontekst jest kopiowany, sortowany i przekazywany jawnie, bez globalnego MDC |
| R12 | Redact zmienia tylko wskazane istniejące pola; oryginał pozostaje niezmieniony |
| R13 | Zdarzenie w LogResult oraz formatter dostają kontekst po maskowaniu |
| R14 | Memory ma dodatnią pojemność i jawną politykę przepełnienia |
| R15 | REJECT_NEW zachowuje rekordy i zgłasza błąd; DROP_OLDEST przyjmuje nowy i usuwa najstarszy |
| R16 | records() i listy raportu są kopiami; clear nie zeruje liczników bufora |
| R17 | Writer dodaje LF i flush, ale nie zamyka pożyczonego zasobu |

Pusty komunikat i pusta wartość pola są poprawne; null nie.
Klucze kontekstu spełniają `[A-Za-z_][A-Za-z0-9_.-]*`, a porządek jest naturalnym
porządkiem String, rozróżniającym wielkość liter. password i Password to różne
klucze, także dla maskowania. Pola JSON są zagnieżdżone pod context, więc
pole kontekstu timestamp nie nadpisuje daty zdarzenia.

Poza zakresem: asynchroniczność, współbieżność, timeouty sinków, MDC/ThreadLocal,
hierarchia loggerów, rotacja, retry, trwałość, format per target, logowanie
Throwable i automatyczne wykrywanie sekretów. Brak transakcji między wyjściami.

## Reprezentacja stanu

Kompozycja rozdziela reprezentację, miejsce zapisu i transformację kontekstu.
Config loggera jest stały; konsola tworzy nową instancję przy poprawnej zmianie.
Sink nadal ma własny stan. Kopia listy targetów nie zamraża jego wnętrza.
Dwa targety mogą wskazywać ten sam sink, co daje dwie próby zapisu.

## Kontrakty operacji i kolejność

Konstruktor bez ostatniego argumentu ContextMasker wybiera identity, czyli
pozostawia pola bez zmian. Maskowanie jest jawną konfiguracją:

```java
var memory = new MemoryLogSink(100, OverflowPolicy.REJECT_NEW);
var logger = new Logger("orders", LogLevel.INFO, new JsonLogFormatter(),
        List.of(new LogTarget("memory", LogLevel.INFO, memory)),
        FailurePolicy.CONTINUE, Clock.systemUTC(),
        ContextMasker.redact(Set.of("password", "token"), "***"));
var context = new LogContext(Map.of("requestId", "r1", "token", "private"));
var result = logger.log(LogLevel.INFO, context, "Przyjęto zamówienie");
```

Zwykłe log(level, message/Supplier) używa pustego kontekstu.
Pozostałe przeciążenia wymagają nie-null LogContext. Każde wywołanie ma swoje
pola; logger nie przechowuje kontekstu poprzedniego logu.

Ścieżka wykonania:

1. Odrzucenie ponownego wejścia i ustawienie active.
2. Walidacja argumentów i sprawdzenie obu progów; jeśli brak trasy, event pusty,
   a wszystkie wyniki FILTERED.
3. Supplier → masker → Clock → LogEvent → formatter.
4. Próby zapisu w kolejności; raport jednej pozycji na target.
5. Zwolnienie active w finally, także po błędzie zależności.

Przeciążenia String sprawdzają nie-null wiadomości przed delegacją.
Masker/formatter zwracający null daje NullPointerException przed zapisami.
Zły klucz, pusta nazwa, brak targetów, duplikat nazwy lub niedodatnia pojemność
dają IllegalArgumentException. Pozostałe programistyczne null dają
NullPointerException. Nie tworzymy atrapy sukcesu po błędzie przygotowania.

### Rekurencja i odzyskiwanie gotowości

Poprawne argumentowo zagnieżdżone wywołanie tego samego loggera daje
[ReentrantLoggingException](src/main/java/pl/jakubtworek/lld/logging/domain/ReentrantLoggingException.java),
także dla poziomu, który normalnie zostałby odfiltrowany.
Z Supplier/maskera/Clock/formattera wyjątek propaguje się przed sinkami.
Z sinka jest RuntimeException zapisu: zewnętrzny log otrzymuje FAILED
i stosuje CONTINUE albo STOP_ON_FAILURE.

Zagnieżdżenie przez inny logger jest dozwolone, ale cykl A → B → A napotka
aktywną instancję A. Flaga nie jest blokadą i nie zapewnia bezpieczeństwa
wielowątkowego. Po dowolnym Error aktywność również jest zwalniana,
choć Error propaguje się i nie powstaje końcowy raport.

### Strukturalna awaria i częściowe powodzenie

Delivery.failure ma Optional<DeliveryFailure>, zawierające exceptionType
i Optional<String> message. Null komunikatu różni się od pustego String.
Nie przechowujemy Throwable, stacktrace ani łańcucha przyczyn.

| Polityka, awaria środkowego targetu | Pierwszy | Zawodny | Ostatni dopuszczony |
| --- | --- | --- | --- |
| CONTINUE | WRITTEN | FAILED | WRITTEN |
| STOP_ON_FAILURE | WRITTEN | FAILED | NOT_ATTEMPTED |

Trasa odfiltrowana pozostaje FILTERED również po STOP.
FAILED może oznaczać częściowy zapis, WRITTEN powrót z wywołania sinka,
a nie fsync czy trwałość. Powtórzenie całego logu może zdublować wcześniejsze
sukcesy. Nie ma wycofania zapisów poprzednich tras.

## Kontekst, maskowanie i reprezentacja

LogContext kopiuje mapę, waliduje klucze/wartości i sortuje pola.
ContextMasker.redact kopiuje także zestaw nazw, więc późniejsza zmiana zestawu
klienta nie zmienia reguły. Nie dodaje nieobecnych pól.
Custom masker powinien być czysty i zwracać nie-null LogContext.

Zarówno LogResult.event, jak i formatter otrzymują wersję po maskowaniu.
Nie zwracamy surowego kontekstu w raporcie, ale caller nadal posiada oryginał.
Maskowanie nie skanuje wiadomości, nazwy loggera ani komunikatu awarii sinka.
Nie gwarantujemy wykrywania sekretów w dowolnym tekście ani czyszczenia pamięci.

Dla pustego kontekstu JSON nie dodaje pola context. Przy polach dodaje
obiekt context w porządku kluczy. TEXT dopisuje `context={key="value",...}`.
Oba formatery kodują quote, backslash, CR/LF/tab i pozostałe ASCII control
poniżej 32 oraz DEL, także w wartościach kontekstu.
Nie deklarujemy kodowania wszystkich separatorów Unicode.
Formatter zwraca rekord bez końcowego separatora; sink dodaje swój zapis.

## Ograniczony bufor i zasoby I/O

`new MemoryLogSink()` oznacza capacity=1000 i DROP_OLDEST.
Wariant z pojemnością oraz OverflowPolicy ustala zachowanie jawnie.

| Pełny bufor | Rekordy | Wynik logu |
| --- | --- | --- |
| REJECT_NEW | Pozostają dotychczasowe | FAILED z SinkCapacityException; polityka loggera decyduje o kolejnych trasach |
| DROP_OLDEST | Najstarszy wypada, nowy wchodzi | WRITTEN; utratę historii ujawnia dropped |

[MemorySinkDiagnostics](src/main/java/pl/jakubtworek/lld/logging/sink/MemorySinkDiagnostics.java)
zwraca capacity, policy, size, rejected i dropped bez zmiany stanu.
REJECT_NEW zwiększa rejected mimo odmowy zapisu; nie obiecujemy braku zmiany
licznika po FAILED. Błędny null nie usuwa rekordów ani nie zwiększa liczników.
Clear opróżnia bufor, zachowując liczniki życia instancji.
Liczniki nasycają się przy Long.MAX_VALUE. Limit dotyczy liczby rekordów,
nie bajtów; pojedynczy duży String nadal może kosztować dużo pamięci.

records() daje kopię listy, więc stare odczyty nie rosną i nie tracą najstarszych
po późniejszych write. WriterLogSink dodaje jeden LF i flush bez close.
Nie otwiera plików; Writer należy do aplikacji. PrintWriter ukrywający IOException
jest sprawdzany przez checkError. Błąd może nastąpić po częściowym zapisie.

## Prześledź maskowanie i przepełnienie

Bufor capacity=1, REJECT_NEW, polityka STOP_ON_FAILURE, target memory przed console:

1. Log z requestId=r1 i password=secret zostaje zamaskowany przed formatowaniem.
2. Pierwsza wiadomość trafia do obu targetów; wynik zawiera password=***.
3. Druga wiadomość napotyka pełny memory: FAILED/SinkCapacityException,
   a console ma NOT_ATTEMPTED. Pierwszy rekord pozostaje w buforze.
4. Diagnostics pokazuje size=1, rejected=1.
5. Clear zwalnia miejsce, ale rejected pozostaje 1.
6. Następny log może zostać zapisany. Kontekst bez password nie odziedziczy
   pola poprzedniego wywołania.

Zmiana na DROP_OLDEST dałaby WRITTEN dla drugiej wiadomości i dropped=1,
kosztem zastąpienia pierwszego rekordu.

## Uruchomienie

Java 21; z katalogu low-level-design/logging-library:

~~~powershell
.\mvnw.cmd --batch-mode --no-transfer-progress clean verify
& "$env:JAVA_HOME/bin/java.exe" -jar target/logging-library-1.0.0-SNAPSHOT.jar
~~~

Demo używa UTF-8, poziomu INFO, TEXT, CONTINUE i stałego czasu 2026-10-23T12:00:00Z. Polecenia obejmują log, level, target, format, policy, context, unset, context-clear, mask, buffer, buffer-info, history, clear i fail. help pokazuje pełną składnię, q kończy program.

## Testy i weryfikacja

[Testy](src/test/java/pl/jakubtworek/lld/logging) sprawdzają leniwe przygotowanie, obsługę błędów, rekurencję, maskowanie, formaty i częściowe awarie Writer. FanOutModelTest porównuje raporty z niezależnym modelem, RingBufferTest sprawdza 40000 zapisów i czyszczenie względem kolejki, a testy escaping kontrolują tabelę ASCII.

| Reguła | Dowód | Granica |
| --- | --- | --- |
| R1–R4 | thresholdMatrixIsInclusive, prepareOnceAndSendSameTextToAllAcceptedTargets | 25 par progów i pojedyncze przygotowanie |
| R5–R8 | failurePolicyControlsOnlyLaterAttempts, preparationFailureTouchesNoSink, partialWriteFailureIsReportedAndContinueStillTriesNext | Obie polityki, trzy miejsca awarii, częściowy zapis |
| R9/R10 | recursionIsRejectedAndLaterCallWorks, errorAlsoReleasesGuard, badMaskerRunsBeforeAnySinkAndGuardRecovers | Supplier/Clock/masker/formatter/sink, dwie polityki i powrót do gotowości |
| R11–R13 | contextCopiesValidatesSortsAndDoesNotLeakBetweenCalls, maskingProtectsResultAndAllFormattedTargetsWithoutMutatingInput | Kopie, kolejność, oryginał i wynik po maskowaniu |
| Reprezentacja | contextEscapingAndOrderingAreDeterministicAndFieldsAreNestedInJson, failureKeepsNullAndEmptyMessagesDistinct | Nested JSON, kontrolne znaki, null vs pusty komunikat |
| R14–R16 | rejectKeepsOldRecordAndReportsFailureThroughLogger, dropOldestAcceptsAndPreservesOrderAndOldSnapshots, nullDoesNotDiscardOldestOrIncrementCounters | Obie polityki, capacity 1/2, clear i odmowa danych |
| R17 | appendsOneLfFlushesAndNeverClosesBorrowedWriter, printWriterHiddenFailureBecomesIOException | LF, flush, brak close i ukryte I/O |
| Model | generatedHistoriesMatchIndependentDeliveryPlan | Dwie polityki, po 100 historii × 100 kroków, 1..6 wyjść |
| Adapter | contextMaskingAndBoundedRejectionAreVisible, dropOldestAndInvalidConfigurationPreserveExpectedHistory | CLI, maskowanie, przepełnienie i zachowanie konfiguracji po błędzie |

## Ograniczenia

Brak asynchroniczności, rotacji plików, hierarchii loggerów, ponawiania zapisów i automatycznego wykrywania sekretów. Wolny odbiorca blokuje całe wywołanie; nie ma timeoutu zapisu.
