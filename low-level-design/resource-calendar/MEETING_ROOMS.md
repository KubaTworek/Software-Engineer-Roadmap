# Rezerwacje sal

Edukacyjny system rezerwacji sal w czystej Javie. Obsługuje dobór sali, raport dostępności, zmianę terminu i anulowanie, z uwzględnieniem wyposażenia oraz bufora po spotkaniu. Stan jest w pamięci, operacje są jednowątkowe.

## Rdzeń techniczny

[BookingService](src/main/java/pl/jakubtworek/lld/meetingrooms/BookingService.java) posiada historię po UUID i uporządkowany katalog sal. Każda sala ma osobny indeks niepokrywających się przedziałów TreeMap oraz mapę ID. W indeksie są wyłącznie potwierdzone rezerwacje z końcem powiększonym o bufor.

R oznacza liczbę sal, B liczbę rezerwacji historycznych, Nmax największy indeks sali, K liczbę zwracanych konfliktów.

| Operacja | Koszt |
| --- | --- |
| Dostępność / raport | O(R log(Nmax + 1) + K), plus przygotowanie wyniku |
| Zapis / zmiana / anulowanie w jednej sali | O(log(N sali + 1)); dobór sali wymaga wcześniejszego wyszukania |
| Wyszukanie w historii | Średnio O(1) |
| Snapshot | O(R + B) po zmianie, O(1) bez zmiany |

Raport kopiuje dane do niemutowalnego wyniku. Kalendarz i historia mają różne role: usunięcie zajętości nie usuwa informacji o anulowaniu. Starsze snapshoty nie odzwierciedlają późniejszych zmian.

### Sprawdzanie sąsiadów i pełny raport

Kalendarz jednej sali zawiera tylko niepokrywające się blokady. Do odpowiedzi
„czy istnieje konflikt?” wystarcza najbliższy poprzednik początku zapytania
i pierwszy odpowiedni następnik. TreeMap pozwala je znaleźć bez skanu historii.

Raport wszystkich konfliktów ma inny koszt. Zapytanie [09:00,14:00) może
przecinać osobne spotkania [10:00,11:00) i [12:00,13:00). Iterator musi zwrócić
oba; zatrzymanie po pierwszym byłoby poprawne dla samego wyboru sali,
lecz nie dla wyjaśnienia odmowy.

Bufor rozszerza koniec blokady, nie czas widocznego spotkania. Dla [10:00,11:00)
i bufora 15 minut indeks zawiera [10:00,11:15). Tak samo przeliczany jest nowy
kandydat, więc późniejsze dodanie wcześniejszego spotkania nie omija przerwy.

Reschedule sprawdza nowy zakres z pominięciem własnego ID przed usunięciem
starego. Jeżeli zmiana się nie uda, kalendarz nadal blokuje pierwotny termin.
Nie następuje automatyczne przeniesienie do innej sali. Anulowanie usuwa
blokadę z indeksu, ale zachowuje historyczny wpis CANCELLED.

## Mechanizmy Javy i model wątkowy

### Kolekcje i wartości czasu w Javie

Indeks zajętości korzysta z TreeMap po Instant, a historia z mapy po UUID.
Instant ma stabilne porównywanie i jest niemutowalny, więc dodanie bufora
przez plus tworzy nową wartość. Nie przesuwa granicy obiektu już użytego
jako klucz drzewa. Duration też jest wartością, a nie odliczającym timerem.

Raport wszystkich kolizji korzysta z iteratora widoku zakresu. tailMap
nie kopiuje wpisów; dopiero lista wynikowa materializuje dane. Nie wolno
traktować tego widoku jako dawnego, stabilnego kalendarza ani zmieniać
źródła podczas jego iterowania.

Wymagane wyposażenie jest kopiowane przez Set.copyOf. Chroni to zbiór
przed dopisaniem elementu przez właściciela wejścia. Record z finalnym
polem Set bez takiej kopii nadal współdzieliłby mutowalny zbiór.
Podobnie immutable lista raportu nie zamraża dowolnego obiektu dodanego
jako jej element; typy składowe również muszą chronić swój stan.

Kolejność mapy historii nie jest porządkiem raportu. Lista sal ma jawny
porządek kodów, a kolizje początku i ID. Comparator musi obejmować
rozstrzygnięcie remisów; stabilność sortowania nie zastępuje tej reguły.

### Jeden odczyt czasu nie oznacza atomowej rezerwacji

Wspólny Instant eliminuje rozbieżności wewnątrz jednej operacji.
Nie zapobiega jednak sytuacji, w której dwa wątki odczytają ten sam wolny
termin. Cały przebieg analiza → wybór → zapis musiałby być chroniony razem
z anulowaniem, zmianą terminu i budową snapshotu. Obecny kod nie ma takiej
synchronizacji; sam Clock ani mapa współbieżna jej nie zapewniają.

Kontrakt kolekcji uporządkowanej: [TreeMap](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/TreeMap.html).

## Zakres, reguły i niezmienniki

- Java 21, Maven, JUnit 5; model jednowątkowy, w pamięci, bez frameworków runtime.
- Stały, niepusty katalog sal o unikalnych kodach, dodatniej pojemności i zbiorze wyposażenia.
- Organizator wyszukuje salę lub automatycznie rezerwuje ją dla liczby osób, sprzętu i terminu.
- Nie ma katalogu użytkowników; UserId deklaruje organizatora i nie stanowi uwierzytelnienia.
- Wymagamy przynajmniej jednej osoby. Sala może mieć więcej miejsc i sprzętu niż żądano.
- Wszystkie wymagane rodzaje sprzętu muszą być obecne; brak wymagań oznaczamy pustym zbiorem.
- Rezerwacja musi zaczynać się nie wcześniej niż bieżąca chwila. Start dokładnie teraz jest dozwolony.
- W tej samej sali nie mogą nakładać się przedziały blokowania potwierdzonych rezerwacji.
  Bufor jest nieujemny, stały dla instancji kalendarza i domyślnie zerowy.
  Sąsiadujące terminy spotkań są dozwolone przy buforze zero.
- Organizator może anulować potwierdzoną rezerwację wyłącznie przed startem, zachowując jej ID w historii.
- Organizator może przed startem zmienić termin potwierdzonej rezerwacji w tej samej sali.
  Nowy start nie może być w przeszłości. ID, temat i wymagania pozostają te same.
- Analiza dostępności pokazuje wszystkie powody odrzucenia każdej sali i konkretne ID konfliktów.
- Użytkownik może rezerwować różne sale w nakładających się terminach. Nie modelujemy kalendarzy osób.
- Wyszukiwanie nie rezerwuje ani nie blokuje zasobu; `book` ponownie sprawdza dostępność.
- Potwierdzona rezerwacja pozostaje CONFIRMED po zakończeniu. Po końcu jej blokady
  (spotkanie wraz z buforem) nie zajmuje późniejszych terminów.
- Nie ma cen, walut, spotkań cyklicznych, powiadomień ani integracji sieciowych.

| ID | Gwarancja | Kontrprzykład / odmowa |
| --- | --- | --- |
| R1 | Katalog niepusty, kody unikalne po normalizacji | Dwie sale A1/a1 → IllegalArgumentException |
| R2 | Wartości poprawne: start < end, liczba osób i pojemność > 0, temat niepusty | Zerowy przedział lub pusty temat → IllegalArgumentException |
| R3 | Nowe wyszukiwanie i rezerwacja mają start >= obserwowana chwila | PAST_START, także gdy koniec jest jeszcze w przyszłości |
| R4 | Przydzielona sala mieści wszystkie osoby i ma wszystkie wymagane urządzenia | Brak odpowiedniego kandydata → NO_AVAILABLE_ROOM |
| R5 | Przedziały blokowania potwierdzonych rezerwacji tej samej sali nie nakładają się | Zawierający, częściowo wspólny termin lub samo przecięcie bufora blokuje salę |
| R6 | Polityka zwraca jednego z odfiltrowanych kandydatów | null, obca lub podmieniona sala → IllegalStateException |
| R7 | Anuluje tylko organizator, tylko CONFIRMED, tylko przed startem | NOT_ORGANIZER, NOT_CONFIRMED, ALREADY_STARTED |
| R8 | Odmowa nie zmienia kalendarza; stare widoki i kolekcje są niemutowalne | Tożsamość snapshotu po błędzie pozostaje ta sama |
| R9 | Anulowanie nie usuwa wpisu, a nowa rezerwacja dostaje nowe ID | Stare ID nie zmieni statusu następnego spotkania |
| R10 | Zmiana terminu zachowuje ID i salę, pomija własny stary przedział, a odmowa zachowuje dawny zapis | Brak automatycznej przeprowadzki do innej wolnej sali |
| R11 | Analiza i zapis używają tej samej oceny, wszystkie powody mogą współistnieć | Za mała, bez projektora i zajęta to trzy równoczesne przyczyny |
| R12 | Bufor rozszerza koniec obu terminów, nie zmienia czasu spotkania; overflow jest błędem przed publikacją | Nie obcinamy blokady do Instant.MAX |

## Reprezentacja stanu

Booking i pozostałe recordy są wartościami niemutowalnymi. Sama deklaracja
`record` nie chroniłaby wnętrza zbioru: konstruktory stosują `Set.copyOf`.
Snapshot kopiuje obie mapy. Zmiana stanu oznacza nowy obiekt rezerwacji i nową
publikację, a nie setter w obiekcie już przekazanym czytelnikowi.

Dozwolone przejście statusu to `CONFIRMED → CANCELLED`. Zmiana terminu pozostawia
status CONFIRMED i ID. Nie ma przywracania ani
ponownego anulowania. Nowe spotkanie po anulowaniu jest osobną rezerwacją.

## Kontrakty i przepływ

| Operacja | Kolejność walidacji | Wynik i efekt |
| --- | --- | --- |
| analyzeAvailability / availableRooms | null, start względem jednego odczytu Clock, rozszerzenie terminu o bufor, ocena cech i kolizji | Niemutowalny raport / lista dostępnych sal według kodu; bez zapisu |
| book | null, temat po strip, start względem jednego odczytu Clock, raport z buforem, kandydaci, wynik polityki | Nowy CONFIRMED i snapshot albo odmowa bez zmiany |
| cancel | null, istniejące ID, zgodny organizator, CONFIRMED, chwila ściśle przed startem | Nowy CANCELLED pod tym samym ID, usunięcie z kalendarza i unieważnienie cache widoku |
| reschedule | null, istniejące ID, zgodny organizator, CONFIRMED, chwila przed pierwotnym startem, nowy start >= ta sama obserwacja Clock, raport z pominięciem własnego ID w tej samej sali | Nowy termin pod tym samym ID albo brak zmiany; identyczny termin zwraca dawny obiekt |
| snapshot | brak obliczeń zależnych od zegara | Bieżący niemutowalny widok |

Nieznane ID daje UNKNOWN_BOOKING. Null oznacza błąd programisty
(NullPointerException), wadliwa wartość IllegalArgumentException. Naruszenie
kontraktu polityki daje IllegalStateException lub propaguje wyjątek polityki.
Przy kilku błędach wygrywa wcześniejszy warunek z tabeli. Bez kandydatów
polityka nie jest wywoływana. Musi być czysta, deterministyczna, bez I/O
i ponownego wejścia do usługi. Przekazana jej lista jest niemutowalna.

NO_AVAILABLE_ROOM zawiera `availability(): Optional<AvailabilityReport>`.
Raport zwykłej rezerwacji obejmuje cały katalog, a zmiany terminu tylko
dotychczasową salę. Odmowy aktora, czasu i nieznanego ID nie mają raportu.
DateTimeException lub ArithmeticException przy dodawaniu bufora propaguje
bez publikacji; konsola prezentuje go jako błąd danych.

### Scenariusz krok po kroku

Zegar pokazuje 08:00 UTC. Ala szuka sali dla 4 osób z projektorem na 09:00–10:00:

1. RoomRequirements i TimeSlot sprawdzają dane, Clock jest odczytany raz.
2. Raport ocenia cechy każdej sali i pobiera konflikty z jej TreeMap.
3. SmallestCapacity wybiera najmniejszą salę ze zbioru dostępnych.
4. Tworzymy kompletny Booking przed zmianą mapy i drzewa terminów.
5. Zatwierdzamy oba indeksy, unieważniamy cache snapshotu.
6. Odmowa anulowania przez obcą osobę pozostawia poprzedni widok.
7. Poprawne anulowanie zachowuje historię CANCELLED, lecz usuwa wpis z drzewa aktywnych terminów.

Przy odrzuconym poleceniu nie publikujemy częściowego wpisu. To ochrona
jednowątkowego przepływu, nie transakcja bazy i nie synchronizacja między wątkami.

### Zmiana terminu bez utraty starego zapisu

`reschedule(id, actor, proposed)` sprawdza, czy organizator zmienia nadal
potwierdzone i nierozpoczęte spotkanie. Ta sama obserwacja Clock służy do
kontroli starego i nowego terminu. Booking tworzy nową wartość z tym samym ID,
salą, wymaganiami i tematem, a usługa ocenia ją wobec pozostałych wpisów.
Wykluczamy dokładnie własne ID, nie wszystkie spotkania tego organizatora.
Nowy przedział może przecinać dawny własny przedział.

Odmowa pozostawia ten sam snapshot i stary termin. Nie wykonujemy osobnego
anulowania przed próbą rezerwacji. Inna wolna sala nie zmienia rozstrzygnięcia:
przeniesienie do innej sali wymagałoby osobnej decyzji użytkownika i kontraktu.
Identyczny termin przechodzi walidację, po czym zwraca oryginalny obiekt
bez publikacji. Nie pozwala to obchodzić zakazu zmiany rozpoczętego spotkania.

### Raport dostępności jako wspólny wynik reguł

`analyzeAvailability(requirements, slot)` zwraca AvailabilityReport z terminem
spotkania, jego blokadą i ocenami sal w kolejności kodów. RoomAvailability ma
flagę niewystarczającej pojemności, zbiór brakującego sprzętu i listę ID
kolidujących potwierdzonych rezerwacji, uporządkowaną według początku i UUID.
Wszystkie trzy przyczyny mogą wystąpić jednocześnie. Puste przyczyny oznaczają
dostępną salę. MeetingRoom.satisfies wykorzystuje tę samą ocenę cech bez konfliktów.

`availableRooms` jest projekcją raportu, a `book` i `reschedule` używają tego
samego wewnętrznego obliczenia. Nie dodajemy osobnego walidatora w konsoli.
Raport jest nieruchomą obserwacją: późniejsze anulowanie nie zmieni dawnych
przyczyn, ale następna analiza uwzględni zwolnioną salę. To wyjaśnienie odmowy,
nie rezerwacja zasobu i nie mechanizm kontroli dostępu do informacji.

### Bufor po spotkaniu i granice arytmetyki

TurnaroundBuffer przechowuje nieujemne Duration, domyślnie zero. Dla spotkania
10:00–11:00 i 15 minut przygotowania przedział blokowania to 10:00–11:15.
Booking.slot nadal zawiera 10:00–11:00; godziny spotkania nie zmieniają się.
Bufor jest stały dla całej instancji i nie zależy od wyposażenia ani sali.

Rozszerzamy **oba** porównywane terminy. Nowe spotkanie 09:00–10:00 też
blokowałoby salę do 10:15, więc nie może poprzedzić istniejącego 10:00–11:00.
Spotkanie kończące się o 09:45 może je poprzedzać, a następne zaczynające się
o 11:15 może następować po nim. Blokady są nadal półotwarte; nie dodajemy
dwukrotnie 15 minut do wymaganego odstępu. Anulowanie zwalnia także bufor.
Wyszukiwanie, zapis i zmiana terminu porównują te same blokady.

Dodawanie Duration może przekroczyć zakres Instant lub arytmetyki pośredniej.
Nie obcinamy wyniku do maksymalnej chwili: byłoby to ukryte skrócenie zajętości.
Wyszukiwanie zgłasza błąd danych, a zapis lub zmiana terminu zachowuje poprzedni
snapshot. Także przy buforze zero reprezentacja zachowuje dokładne nanosekundy.

### Dlaczego kolizja ma dwie ścisłe nierówności?

Dwa niepuste przedziały A i B nakładają się, gdy
`A.start < B.end && B.start < A.end`. To zaprzeczenie rozłączności
`A.end <= B.start || B.end <= A.start`. Warunek obejmuje identyczne terminy,
częściowe przecięcie i pełne zawieranie. Równość końca z początkiem oznacza
brak kolizji. Nie odejmujemy „ostatniej nanosekundy” ani nie zaokrąglamy chwil.

### Czas, offset i granica anulowania

Domena przechowuje Instant. Konsola używa OffsetDateTime, wymaga offsetu
lub Z i konwertuje do Instant. `2030-01-01T10:00+01:00` oraz
`2030-01-01T09:00Z` to ta sama chwila i ten sam termin sali.
Nie przyjmujemy samego `2030-01-01T10:00`, bo jego znaczenie zależy od strefy.
Offset nie opisuje przyszłych zmian czasu w strefie Europe/Warsaw; nie
implementujemy rozstrzygania lokalnych luk i podwójnych godzin DST.

Start dokładnie teraz można zarezerwować, ale od razu nie można go anulować:
warunek rezerwacji to `start >= now`, anulowania `now < start`. Po końcu
spotkania nie zmieniamy automatycznie statusu. Dostępność wynika z przecięcia
blokad, więc po końcu spotkania sala może być jeszcze zajęta przez bufor.
Po końcu blokady można ponownie użyć sali. Zegar jest zaufanym źródłem;
nie dodajemy ochrony przed jego cofnięciem ani schedulerów.

## Uruchomienie

Java 21; z katalogu low-level-design/resource-calendar:

~~~powershell
.\mvnw.cmd --batch-mode --no-transfer-progress clean verify
& "$env:JAVA_HOME/bin/java.exe" -jar target/resource-calendar-1.0.0-SNAPSHOT.jar smallest 15
~~~

Parametry: `[first|smallest] [BUFFER_MINUTES]`. Konsola udostępnia rooms, available, explain, book, reschedule, cancel i bookings; `help` podaje format terminów i argumentów, `q` kończy demo.

## Testy i weryfikacja

[Testy](src/test/java/pl/jakubtworek/lld/meetingrooms) obejmują uprawnienia organizatora, pełne raporty kolizji, bufor w obu kolejnościach zapisu, cofnięcie nieudanej zmiany i stabilność snapshotów. TimeSlotTest sprawdza 441 par przedziałów; GeneratedCalendarTest porównuje sekwencje z niezależnym modelem siatki czasu.

| Reguła | Test / plik | Granica gwarancji |
| --- | --- | --- |
| R1–R2: wartości i własność sprzętu | [ValueObjectsTest](src/test/java/pl/jakubtworek/lld/meetingrooms/ValueObjectsTest.java), invalidCatalogArgumentsAndSubjectCannotPublish w BookingServiceTest | Kody, pojemność, wymagania, temat, kopie zbiorów i duplikaty |
| R5: geometria przedziałów | [TimeSlotTest](src/test/java/pl/jakubtworek/lld/meetingrooms/TimeSlotTest.java) | Wszystkie 441 par na siatce 0..6 oraz osobne przypadki nanosekundowe |
| R3, R7: zegar | startNowCanBeBookedButCannotBeCancelledAndPastStartsReject, cancellationRequiresOrganizerConfirmedStatusAndStrictlyFutureStart | Teraz, przeszłość, nanosekunda przed startem, start i koniec |
| R4–R6: wybór sali | equipmentCapacityAndBusyRoomsFilterBeforeSelection, strategiesSelectDifferentRoomsAndResolveCapacityTiesByCode, malformedFailingAndMutatingStrategiesCannotPublish | Cechy i kolizje przed polityką; dwie preferencje i wadliwe implementacje |
| R8–R9: publikacja i anulowanie | bookConflictCancelAndReusePreserveHistoricalView, inputAndSnapshotCollectionsCannotMutateCalendar | Stare ID i widoki, zachowanie snapshotu po odmowie |
| R3–R12: sekwencje operacji | [GeneratedCalendarTest](src/test/java/pl/jakubtworek/lld/meetingrooms/GeneratedCalendarTest.java) | 4 ziarna × 2 bufory × 250 poleceń, niezależna siatka zajętości |
| R10: zmiana terminu | [BookingExtensionsTest](src/test/java/pl/jakubtworek/lld/meetingrooms/BookingExtensionsTest.java): rescheduleKeepsIdentityRoomAndDetailsWhileIgnoringOwnOldInterval, conflictingMoveCannotSwitchToAnotherRoomAndReportsExactBlocker | To samo ID i sala, własny stary przedział pomijany, odmowa nie gubi terminu |
| R11: powody niedostępności | ten sam plik: reportListsAllReasonsAndAllConflictsAndMatchesBookingRefusal | Jednoczesne powody, wszystkie konflikty, zgodność raportu odczytu i odmowy, niemutowalność |
| R12: bufor i arytmetyka | ten sam plik: bufferRejectsTooCloseMeetingsOnBothSidesAndAllowsExactBoundary, bufferOverflowDuringSearchBookingAndMovePreservesPublication | Oba kierunki wstawiania, dokładna granica, zero i przepełnienie Instant |
| Adapter, offsety, zakończenie | [BookingConsoleTest](src/test/java/pl/jakubtworek/lld/meetingrooms/console/BookingConsoleTest.java) | Strumienie w pamięci, różne offsety tej samej chwili, błędne dane i IOException |

## Ograniczenia

Brak rezerwacji cyklicznych, odwoływania rozpoczętych spotkań, przenoszenia rezerwacji do innej sali, trwałości i współbieżności. Instant opisuje jednoznaczny moment; prezentacja lokalnych stref czasowych pozostaje poza zakresem.
