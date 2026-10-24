# System wind

Projekt edukacyjny w czystej Javie: deterministyczna symulacja grupy wind, przydzielanie wezwań i obsługa kolejek przystanków. Czas jest wyrażony przez jawne kroki tick, bez zegara i opóźnień. Stan znajduje się w pamięci; sterowanie jest jednowątkowe.

Materiały do nauki: [lektury i źródła tego ćwiczenia](../MATERIALY.md#windy).

## Rdzeń techniczny

[StopQueue](src/main/java/pl/jakubtworek/lld/elevators/StopQueue.java) jest niemutowalnym FIFO z dwiema listami wiązanymi i 32-poziomowym trie przynależności liczb int. Dopisanie współdzieli istniejące węzły, a indeks zapobiega duplikatom bez liniowego szukania.

C to liczba wind, Q liczba przystanków, H liczba aktywnych wezwań.

| Operacja | Koszt |
| --- | --- |
| Sprawdzenie / dopisanie celu | Stałe 32 poziomy trie; dopisanie listy O(1) |
| Usunięcie początku FIFO | O(1) amortyzowane w liniowej historii; O(Q) przy odwróceniu tylnej listy |
| Estymacja dojazdu | O(Q), bez symulowania każdego piętra |
| Wybór najbliższej / najmniej obciążonej | O(C log C) z porządkowaniem kandydatów |
| Wybór według czasu dojazdu | O(C × (1 + Qmax)) |
| Tick całej grupy | Pesymistycznie O(C log C + suma Q + H), wraz z odwracaniem kolejek i widokiem |

Pamięć bieżącego stanu jest liniowa względem wind, przystanków i wezwań, ze stałym kosztem trie na cel. Zachowanie starszych wersji utrzymuje współdzielone węzły; rozgałęzianie historii może powtarzać odwrócenie tej samej tylnej listy, więc amortyzacja nie obejmuje dowolnego drzewa wersji. Obliczenia odległości używają long, aby obsłużyć krańcowe wartości pięter int.

### Dwie listy i indeks przynależności

Początek FIFO znajduje się na przedniej liście, nowe cele trafiają na tylną
w odwrotnej kolejności. Gdy przednia się wyczerpie, tylna jest odwracana.
W jednej liniowej historii każdy element bierze udział w takim przeniesieniu
najwyżej raz; stąd koszt amortyzowany O(1), mimo pojedynczej operacji O(Q).

Sam zestaw list nie wykrywa duplikatu szybko. Trie rozkłada wszystkie 32 bity
numeru piętra, więc ścieżka ma stałą długość również dla liczb ujemnych.
Dodanie lub usunięcie celu kopiuje ścieżkę indeksu; starsza kolejka zachowuje
własny widok. Iterowanie po liście przez kolejne get(i) prowadziłoby do O(Q²);
pełne przejście korzysta z iteratora.

Estymator oblicza liczbę kroków z odległości między kolejnymi celami oraz
otwarcia i zamykania drzwi. Nie wykonuje miliona tick dla miliona pięter.
Bliska winda może mieć gorszy czas odbioru, jeżeli wcześniej obsługuje daleki
cel FIFO. Dlatego odległość geometryczna i czas odbioru mogą dać inny przydział.

## Mechanizmy Javy i model wątkowy

### AbstractList, iterator i koszt ukryty za interfejsem

StopQueue implementuje `AbstractList<Integer>`, ale własny Link przechowuje
piętro jako int. Dostęp przez `List<Integer>` i iterator wymaga boxingu.
Reprezentacja prymitywna w węźle nie usuwa kosztu granicy generycznego API.

get(i) przechodzi od początku, więc pętla for z get(i) miałaby O(Q²).
Nadpisany iterator przechodzi przednią listę raz i używa `ArrayDeque<Integer>`
dla tylnej. listIterator buduje jednorazowo listę pomocniczą, aby operacje
odziedziczone po API listy nie powielały liniowego wyszukiwania po indeksie.
Ten wybór kosztuje dodatkowe O(Q) referencji podczas takiego przejścia.

Link i Bits są recordami z finalnymi odnośnikami do innych niemutowalnych
węzłów. Kopiowanie ścieżki zachowuje starsze korzenie bez klonowania całej
kolejki. GC odzyska odłączone węzły dopiero wtedy, gdy nie prowadzi do nich
żadna zachowana wersja ani aktywny iterator.

Indeks trie odczytuje wszystkie 32 bity int, również bit znaku.
Obliczenie odległości musi rozszerzyć argument przed odejmowaniem:
(long) target - position. Rzutowanie gotowej różnicy po przepełnieniu int
nie naprawiłoby wyniku. Suma estymacji używa Math.addExact.

### Tick jest symulacją, nie wątkiem

Każdy tick jest zwykłym synchronicznym wywołaniem. Nie ma Thread.sleep,
harmonogramu ani osobnego wątku dla kabiny; liczba kroków nie oznacza
czasu procesora. Pozwala to powtarzalnie testować kolejność stanów.

Równoległe call i tick musiałyby uzgodnić kolejkę oraz mapę przypisań.
Niemutowalne Elevator nie chroni mutowalnego właściciela grupy przed
utraconym zapisem. Aktualny kontrakt obejmuje jednego wykonawcę komend.
API stosu pomocniczego: [ArrayDeque](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/ArrayDeque.html).

## Zakres, reguły i niezmienniki

Pasażer wzywa windę lub wybiera cel konkretnej kabiny. Operator wyłącza i włącza
windę. Są to role scenariusza, bez modelu kont i autoryzacji. Wszystkie windy
obsługują ten sam pełny zakres pięter. Budynek może mieć ujemne piętra garażu.
Konfiguracja początkowa wymaga niepustej listy kabin, bez kolejki, z zamkniętymi
drzwiami i kierunkiem IDLE. Nie importujemy częściowego przebiegu z innego zespołu.

Przydział wezwania jest natychmiastowy, obsługa następuje później przez tick.
Gdy wybrana kabina ma już otwarte drzwi na żądanym piętrze, odbiór jest natychmiastowy
i nie zapisujemy oczekującego wezwania. Przy zamkniętych drzwiach ten sam cel
wymaga kroku otwierającego. DRAINING nie bierze udziału w nowym przydziale,
ale istniejący duplikat wezwania nadal wskazuje wcześniej przypisaną kabinę.
Polecenie selectFloor dla DRAINING jest odrzucane, także gdy powtarza dawny cel;
to jawna odmowa polecenia, a nie trwała deduplikacja intencji.
Nie ma limitu liczby przyjętych żądań, lecz każdy
unikalny numer piętra występuje najwyżej raz w kolejce danej kabiny.

Clock nie jest potrzebny: żadna reguła nie używa czasu środowiska. Nie ma
Thread.sleep, zadań okresowych ani osobnych wątków kabin. Tick dotyczy wszystkich
wind jako jeden przyrost modelu, niezależnie od kolejności elementów mapy.
Nie modelujemy pieniędzy, fizycznej pojemności, wsiadania lub wysiadania osób.

- R1: budynek ma co najmniej dwa piętra; windy mają unikalne kody i ten sam zakres pięter.
- R2: żądanie musi wskazywać piętro budynku; cele w kolejce są unikalne i zachowują FIFO.
- R3: przy otwartych drzwiach nie ma ruchu, a kierunek wynosi IDLE.
- R4: tick zamyka otwarte drzwi albo jedzie najwyżej o jedno piętro do pierwszego celu.
- R5: osiągnięcie celu otwiera drzwi i usuwa tylko pierwszy przystanek.
- R6: wezwanie z piętra ma najwyżej jeden aktywny przydział; duplikat zwraca ten sam kod windy.
- R7: dyspozytor wybiera tylko spośród wind IN_SERVICE, a wynik polityki jest sprawdzany.
- R8: wyłączenie wymaga pustej kolejki, zamkniętych drzwi i kierunku IDLE.
- R9: odmowa nie publikuje zmiany; stare widoki i ich kolekcje pozostają niemutowalne.
- R10: zakończony przystanek usuwa przypisane wezwanie; wyłączona winda nie przyjmuje celów i nie jeździ.
- R11: DRAINING zachowuje istniejące cele i wezwania, nie przyjmuje nowych, a po ostatnim zamknięciu drzwi przechodzi do OUT_OF_SERVICE.
- R12: estymacja nowego odbioru dotyczy tylko IN_SERVICE, jest odczytem i uwzględnia FIFO oraz drzwi.
- R13: TickResult zawiera dokładnie stan danego kroku i niemutowalne fakty w deterministycznej kolejności.

## Reprezentacja stanu

Klasa Elevator chroni swój stan przez zwracanie nowej
wartości, a metody transformujące są pakietowe. Grupa publikuje tę wartość razem
z mapą przydziałów. Konsola nie może samodzielnie przestawić piętra lub drzwi.

Osobne enumy rozdzielają kierunek, drzwi i tryb. Nie każda kombinacja jest
osiągalna: OPEN zawsze ma IDLE, a OUT_OF_SERVICE ma CLOSED, IDLE i pustą kolejkę.
DRAINING może jechać albo mieć otwarte drzwi. Gdy po ostatnim celu zamknie
drzwi, automatycznie kończy wycofywanie.
Prywatny konstruktor pełnego stanu jest wywoływany tylko przez kontrolowane
przejścia kabiny. Publiczny konstruktor tworzy wyłącznie bezczynną kabinę.

## Kontrakty operacji i przejścia

| Operacja | Kolejność warunków | Efekt |
| --- | --- | --- |
| call(floor) | zakres piętra, istniejący przydział, lista IN_SERVICE, polityka i kontrola jej wyniku | Kod windy; nowy cel i przydział lub natychmiastowy odbiór przy OPEN |
| selectFloor(id, floor) | null, znany kod, zakres piętra, IN_SERVICE | Dopisuje unikalny cel na koniec FIFO; duplikat bez publikacji |
| takeOutOfService(id) | null, znany kod, nie OUT_OF_SERVICE, pusta kolejka/CLOSED/IDLE | OUT_OF_SERVICE albo odmowa bez zmiany |
| returnToService(id) | null, znany kod, tryb OUT_OF_SERVICE lub DRAINING | IN_SERVICE z zachowaniem piętra, drzwi, kierunku i kolejki |
| beginDraining(id) | null, znany kod, nie OUT_OF_SERVICE, nie DRAINING | DRAINING z zachowaniem kolejki albo od razu OUT_OF_SERVICE, jeśli kabina bezczynna i zamknięta |
| estimatePickup(id, floor) | null, znany kod, zakres, IN_SERVICE | long kroków, bez zmiany stanu |
| tick() | lokalne przejście każdej kabiny, usunięcie obsłużonych przydziałów, budowa zdarzeń | TickResult ze snapshotem i listą faktów; przy bezczynności ten sam snapshot i pusta lista |
| snapshot() | bez dodatkowych obliczeń | Niemutowalny widok bieżącej publikacji |

Nieznany kod daje UNKNOWN_ELEVATOR, piętro spoza budynku INVALID_FLOOR.
Brak wind IN_SERVICE → NO_AVAILABLE_ELEVATOR, chyba że wezwanie już ma przydział.
Próba celu dla wyłączonej
kabiny → OUT_OF_SERVICE, wyłączenia zajętej → NOT_IDLE. Powtórne przełączenie
trybu daje ALREADY_OUT_OF_SERVICE lub ALREADY_IN_SERVICE. beginDraining dla
OUT_OF_SERVICE daje OUT_OF_SERVICE, a powtórne wycofywanie ALREADY_DRAINING.
Cel lub estymacja dla DRAINING daje DRAINING. returnToService można wywołać
także podczas wycofywania: zachowuje kolejkę, ruch i drzwi, zmienia tylko tryb.
Null to błąd
programisty (NullPointerException); zła konfiguracja IllegalArgumentException.
Przy wielu naruszeniach wygrywa pierwszy warunek w tabeli.

Polityka dostaje niepustą niemutowalną listę kabin IN_SERVICE, uporządkowaną
według kodu. Musi być czysta, deterministyczna, bez I/O i ponownego wejścia do
grupy. Wynik null albo ID spoza kandydatów daje IllegalStateException, a wyjątek
polityki propaguje. Żaden z tych błędów nie zapisuje częściowego przydziału.

### Jeden krok kabiny

1. OUT_OF_SERVICE: pozostaje bez zmian.
2. OPEN: zamknij drzwi, zachowaj piętro i cele; kierunek IDLE. Nie jedź w tym kroku.
   Jeżeli tryb to DRAINING i kolejka jest pusta, przejdź też do OUT_OF_SERVICE.
3. CLOSED i pusta kolejka: pozostaje bez zmian.
4. CLOSED i cel na bieżącym piętrze: otwórz drzwi, usuń głowę, kierunek IDLE.
5. CLOSED i cel dalej: przesuń o jedno piętro ku głowie FIFO. Przy osiągnięciu
   celu otwórz drzwi i usuń głowę; w przeciwnym razie kierunek UP albo DOWN.

Kierunek IDLE nie oznacza pustej kolejki: winda może właśnie otworzyć lub
zamknąć drzwi i nadal mieć następne cele. W modelu dyskretnym kierunek UP/DOWN
opisuje kontynuowany przejazd do głowy kolejki, nie sygnał fizycznego silnika.
Nie ma ręcznych komend otwierania drzwi ani wymuszania piętra.

### Scenariusz: od wezwania do końca przejazdu

Początkowo E1 jest na 0, E2 na 8. NearestElevator przydziela wezwanie 2 do E1:

| Krok | E1 | Kolejka | Wezwanie 2 |
| --- | --- | --- | --- |
| call(2) | 0, CLOSED, IDLE | [2] | Przypisane do E1 |
| tick | 1, CLOSED, UP | [2] | Nadal oczekuje |
| tick | 2, OPEN, IDLE | [] | Usunięte po otwarciu |
| selectFloor(E1, 4) | 2, OPEN, IDLE | [4] | Już obsłużone |
| tick | 2, CLOSED, IDLE | [4] | Brak |
| tick | 3, CLOSED, UP | [4] | Brak |
| tick | 4, OPEN, IDLE | [] | Brak |
| takeOutOfService(E1) | Odmowa NOT_IDLE | [] | Bez zmiany snapshotu |
| tick, potem wyłączenie | 4, CLOSED, IDLE, OUT_OF_SERVICE | [] | Brak |

Nowy korzeń trwałej kolejki i mapa wezwań powstają przed publikacją snapshotu. Starszy
widok nadal pokaże wcześniejsze piętro i kolejkę. `final` samo nie chroniłoby
kolejki: Elevator współdzieli niemutowalne ogniwa StopQueue, a snapshot kopiuje mapy. Brak zmiany (np.
duplikat aktywnego wezwania lub tick bezczynnego zespołu) zachowuje ten sam widok.

### Dlaczego przejazd przez piętro nie wystarcza?

Dla kolejki [3,1] od piętra 0 winda przejedzie przez 1 z zamkniętymi drzwiami,
obsłuży 3, zamknie drzwi, a potem wróci na 1. To FIFO, nie sterowanie zbiorowe.
Wezwanie na 1 znika dopiero po otwarciu drzwi przypisanej kabiny na 1. Inna
winda otwierająca tam drzwi nie realizuje tego przydziału. Takie przejęcie
wymagałoby jawnej reguły przenoszenia żądań, obecnie niezaimplementowanej.

### Estymacja kroków i jej założenia

PickupEstimator odpowiada, po ilu tick wybrana kabina otworzy drzwi na piętrze
odbioru. Dla IN_SERVICE z OPEN na tym piętrze wynik to zero. W innych
przypadkach początkowe OPEN kosztuje jeden krok zamknięcia. Następnie dla
każdego wcześniejszego celu FIFO sumujemy `max(1, abs(target−position))`.
Gdy cel jest na bieżącym piętrze, jedno tick nadal musi otworzyć zamknięte drzwi.
Między celami doliczamy jeden krok ich zamknięcia.

Jeżeli piętro odbioru jest już w kolejce, kończymy na nim bez uwzględniania
późniejszych celów. Inaczej dopisujemy koszt odbioru po całej kolejce, tak jak
requestStop dopisałoby nowy unikalny przystanek. Nie zmieniamy kabiny ani
przydziałów. Obliczenia używają long i Math.addExact; przepełnienie sumy
propaguje bez publikacji. Koszt zależy od liczby celów, nie liczby pięter.

Przykład: E1 na 0 z [10,9] odbierze piętro 1 po `10+1+1+1+8 = 21` krokach.
E2 na 8 z pustą kolejką potrzebuje 7. Nearest wybierze E1, FewestPickupSteps
E2. To estymacja w krokach modelu, nie sekundach; zakłada obecny FIFO i brak
zmian trybu lub usunięcia/przestawienia celów w trakcie. Nowe cele dopisywane
za już przyjętym odbiorem nie zmieniają jego pozycji. Zmiana algorytmu jazdy
wymaga zmiany estymatora i ponownego porównania go z symulacją.

### DRAINING: przyjmowanie i wykonywanie pracy

Natychmiastowe `out` odmawia przy kolejce
lub otwartych drzwiach. `drain` to odrębna operacja. Dla zajętej kabiny ustawia
DRAINING bez utraty celów i przydziałów. Nowe call nie rozważają jej jako
kandydata, a selectFloor jest odrzucane. Duplikat już przydzielonego call
zwraca ten sam kod, nawet gdy żadna kabina nie jest już IN_SERVICE.

Winda DRAINING wykonuje normalne kroki. Po otwarciu drzwi na ostatnim celu
nadal jest DRAINING. Dopiero zamknięcie drzwi zmienia tryb na OUT_OF_SERVICE.
Bezczynna, zamknięta kabina przechodzi tam od razu w poleceniu drain.
`in` może cofnąć DRAINING w dowolnym momencie, zachowując dotychczasową pracę.
Nie przenosimy wezwań i nie modelujemy nagłego przerwania jazdy.

### Typowane zdarzenia zakończonego kroku

TickResult zwraca snapshot oraz niemutowalną listę ElevatorEvent:

- Moved: kod windy i piętra przed/po przesunięciu o jeden;
- DoorsOpened i DoorsClosed: kod i piętro;
- HallCallServed: wezwanie obsłużone przez przypisaną windę;
- ModeChanged: zmiana DRAINING → OUT_OF_SERVICE po końcowym zamknięciu.

Kolejność: rosnący kod windy, a wewnątrz kabiny ruch, zmiana drzwi, obsłużenie
wezwania, zmiana trybu. Zatem dojazd może dać Moved, DoorsOpened i HallCallServed
w jednym tick. Otwarcie na bieżącym piętrze nie daje Moved, a zwykły cel kabiny
nie tworzy HallCallServed. Bezczynny krok daje pustą listę i ten sam snapshot.

Stan i kompletna lista faktów powstają przed jednym przypisaniem publikującym
wynik. Konsola jedynie formatuje fakty. Nie utrzymujemy historii zdarzeń ani
trwałego event sourcingu: call, select, drain, out i in nie zwracają tych
zdarzeń. Natychmiastowe przejście trybu przez komendę drain jest widoczne
w jej wyniku/statusie, nie jako ModeChanged następnego bezczynnego tick.
Zdarzenia i snapshoty zachowane przez odbiorcę pozostają dawnymi wartościami.

## Uruchomienie

Java 21; z katalogu low-level-design/elevator-system:

~~~powershell
.\mvnw.cmd --batch-mode --no-transfer-progress clean verify
& "$env:JAVA_HOME/bin/java.exe" -jar target/elevator-system-1.0.0-SNAPSHOT.jar eta
~~~

Parametr: `nearest|least|eta`. Demo ma piętra −2–10, E1 na 0 i E2 na 8. Polecenia: `call FLOOR`, `select EID FLOOR`, `step [N]`, `eta EID FLOOR`, `drain EID`, `out EID`, `in EID`, `status`, `help`, `q`; N mieści się w 1–1000.

## Testy i weryfikacja

[Testy](src/test/java/pl/jakubtworek/lld/elevators) obejmują przydziały, drzwi, tryby i kolejność zdarzeń. StopQueueTest porównuje 6000 operacji z ArrayDeque, kontroluje skrajne int i stare korzenie; testy generowane sprawdzają historie całej grupy.

| Reguła | Test / plik | Granica dowodu |
| --- | --- | --- |
| R1–R2: wartości, konfiguracja i kopie | [ElevatorTest](src/test/java/pl/jakubtworek/lld/elevators/ElevatorTest.java): valuesRejectInvalidConfigurationAndProtectQueue; ElevatorGroupTest: invalidCatalogAndNullArgumentsFailBeforePublication | Kody, zakres, pusta lista, duplikaty, inny budynek i null |
| R3–R5: ruch i FIFO | ElevatorTest: movementArrivalAndDoorClosingAreSeparateTransitions, fifoCanPassLaterStopWithoutServingItAndReverseAfterFirstTarget | Przejazd w obie strony, cele bieżącego piętra, rozdzielenie zamykania i ruchu |
| R6, R10: przydziały | ElevatorGroupTest: activeHallCallIsDeduplicatedAndServedOnlyByAssignedCar, arrivalOfOtherCarDoesNotClearAssignedCall, hallRequestCanAttachToExistingCabinStopWithoutDuplicateQueueEntry | Duplikat, przypisana kabina, wspólny cel z panelu i piętra |
| R7: polityki i liczby | ElevatorGroupTest: dispatchPoliciesDifferWhenNearbyCarHasMorePendingStops, dispatchDistanceAcrossFullIntegerRangeUsesLongAndStableTieBreak, failingMutatingNullForeignAndDisabledSelectionsCannotPublish | Preferencje, remisy, pełny int i wadliwe polityki |
| R8: serwis | ElevatorTest: serviceModeRequiresIdleAndRejectsRepeatedTransitions; ElevatorGroupTest: disabledCarIsExcludedAndRestorationMakesItEligibleAgain | Pusta kolejka, otwarte drzwi, powtórne przejścia i przywrócenie |
| R9: publikacja | ElevatorGroupTest: inputCollectionsSnapshotsAndDuplicateTargetsCannotMutatePublishedState | Stare mapy, kabiny i kolejki, tożsamość po braku zmiany |
| R11: łagodne wycofywanie | [DrainingAndEventsTest](src/test/java/pl/jakubtworek/lld/elevators/DrainingAndEventsTest.java): drainingFinishesExistingRequestsRejectsNewOnesAndDisablesAfterClosingDoors, existingHallCallRemainsValidWhenNoCarAcceptsNewCalls | Przyjęte cele, brak nowych, duplikat, końcowe zamknięcie i cofnięcie wycofywania |
| R12: kroki do odbioru | [PickupEstimatorTest](src/test/java/pl/jakubtworek/lld/elevators/PickupEstimatorTest.java) | 5200 porównań z rzeczywistą symulacją, skrajne liczby, trzy preferencje i odczyt bez mutacji |
| R13: fakty kroku | DrainingAndEventsTest: eventsHaveStableCarOrderAndNoFakeHallCallForCabinDestination, currentFloorPickupDoesNotEmitMovementAndIdleTickHasNoEvents | Dokładna kolejność, brak fałszywych zdarzeń, niezmienność zachowanego wyniku |
| R2–R13: dłuższe historie | [GeneratedGroupTest](src/test/java/pl/jakubtworek/lld/elevators/GeneratedGroupTest.java) | 4 ziarna × 300 poleceń, DRAINING, odtwarzanie zdarzeń i późniejsze opróżnienie |
| Adapter i zakończenie | [ElevatorConsoleTest](src/test/java/pl/jakubtworek/lld/elevators/console/ElevatorConsoleTest.java) | Strumienie w pamięci, błędy wejścia, EOF, q i IOException |

## Ograniczenia

Brak pasażerów, pojemności kabin, kierunkowych kolejek wezwań, czasu fizycznego i wielowątkowego sterowania. FIFO nie optymalizuje globalnej trasy.
