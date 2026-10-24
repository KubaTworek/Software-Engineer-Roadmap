# Kalendarz zasobów

Projekt edukacyjny w czystej Javie: rezerwowanie zasobów przez indeksowanie niepokrywających się przedziałów. Zawiera rezerwacje sal na osi Instant i wynajem samochodów na osi LocalDate. Stan jest w pamięci; operacje wykonuje jeden wątek.

Materiały do nauki: [lektury i źródła tego ćwiczenia](../MATERIALY.md#kalendarz-zasobów).

## Rdzeń techniczny

[IntervalCalendar](src/main/java/pl/jakubtworek/lld/calendar/IntervalCalendar.java) łączy TreeMap po początku przedziału z HashMap po ID. Czas i klucze muszą mieć stabilne porównywanie oraz równość.

Wyszukanie konfliktu zaczyna od przedziału poprzedzającego początek zapytania. Jeśli kończy się on wcześniej, przechodzi do pierwszego następnego wpisu. Iterator odwiedza kolejne przedziały tylko do końca zakresu zapytania. Poprawność tego podejścia zależy od zakazu nakładania zapisanych przedziałów.

N to liczba wpisów jednego indeksu, K liczba znalezionych konfliktów.

| Operacja | Czas |
| --- | --- |
| Pierwszy konflikt | O(log(N + 1)) |
| Wszystkie konflikty | O(log(N + 1) + K), wynik uporządkowany po początku |
| Zapis / przeniesienie / usunięcie | O(log(N + 1)); wyszukanie ID średnio O(1) |
| Pamięć | O(N), plus O(K) dla listy wynikowej |

Lista wynikowa jest niemutowalna. Przy raportowaniu R niezależnych zasobów koszt wyszukiwań rośnie do O(R log(Nmax + 1) + suma K), osobno dochodzi przygotowanie raportu.

## Mechanizmy Javy i model wątkowy

### NavigableMap: widok zakresu, iterator i porządek kluczy

TreeMap jest drzewem czerwono-czarnym. floorEntry(start) szuka największego
początku nieprzekraczającego start, a ceilingEntry(start) najmniejszego
nie mniejszego od start. To operacje po uporządkowanych kluczach,
bez budowania pomocniczej listy wszystkich wpisów.

tailMap(key, true) jest widokiem zakresu tej samej mapy, nie jej kopią.
Iterator values() przechodzi kolejne węzły. Po jednym wyszukaniu początku
można odczytać K trafień w O(log N + K); ponowne wywoływanie higherEntry
dla każdego wpisu dodawałoby koszt wyszukiwania drzewa na każdy krok.

Granice T implementują `Comparable<? super T>`. Ta deklaracja dopuszcza
porównywanie również przez nadtyp; nie zmienia jednak znaczenia compareTo.
TreeMap utożsamia dwa klucze, gdy porównanie daje zero, natomiast HashMap
po ID korzysta z equals/hashCode. Oba kontrakty muszą być stabilne.
Wpisy niepokrywające się nie mogą mieć tego samego początku, więc jeden
klucz czasu jednoznacznie wskazuje Slot.

Slot jest recordem, ale ogólne K, T i V nie stają się przez to automatycznie
niemutowalne. List.copyOf wyniku kopiuje strukturę listy, nie zawartość V.
To kontrakt właściciela wartości, a nie głębokie kopiowanie przez generyki.

### Iterator nie zapewnia synchronizacji

Zmiana TreeMap podczas przechodzenia widoku może unieważnić iterator.
Fail-fast i ConcurrentModificationException służą wykrywaniu błędów;
nie są blokadą ani gwarancją zauważenia każdego wyścigu. Kod nie zmienia
indeksu podczas wyszukiwania i wymaga jednego wątku.

Dla wielu wątków sprawdzenie konfliktu i put musiałyby należeć do tej samej
sekcji krytycznej. Synchronizacja samego zapisu zostawiłaby przerwę między
oceną wolnego zakresu a jego zajęciem.
Szczegóły API: [TreeMap](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/TreeMap.html).

## Zakres i kontrakty

[Rezerwacje sal](MEETING_ROOMS.md) obejmują pojemność, wyposażenie, bufor między spotkaniami i raport wszystkich konfliktów. [Wynajem aut](CAR_RENTAL.md) obejmuje rezerwacje, odbiór, zwrot, opłaty i blokady serwisowe.

Wspólny indeks przechowuje przedziały półotwarte [start, end): początek należy do przedziału, koniec już nie. Początek musi być wcześniejszy od końca. Sąsiednie [10,11) i [11,12) są dozwolone. W obrębie jednego indeksu zapisane przedziały nie mogą się nakładać; zapytanie może obejmować wiele z nich.

ID, wartość i granice nie mogą być null. Aktualizacja istniejącego ID sprawdza konflikt z pominięciem jego własnego wpisu, zanim usunie stary przedział. Nieudana zmiana zachowuje dotychczasową rezerwację. Nieznany identyfikator usuwania nie zmienia indeksu.

## Wyszukiwanie i aktualizacja krok po kroku

Dwa przedziały A i B nakładają się wtedy i tylko wtedy, gdy
A.start < B.end oraz B.start < A.end. Ścisłe porównania pozwalają umieścić
koniec jednej rezerwacji dokładnie na początku drugiej. Nie trzeba rozwijać
przedziału do listy chwil lub dni; liczba jednostek czasu nie zmienia kosztu porównania.

### Dlaczego wystarcza poprzednik

Po wyszukaniu floorEntry(start) każdy jeszcze wcześniejszy przedział musi
kończyć się przed początkiem znalezionego wpisu. Gdy poprzednik nie przecina
zapytania, wcześniejsze wpisy również nie przecinają. Można więc przejść
bezpośrednio do pierwszego następnego początku i iterować tylko po kandydatach
do chwili osiągnięcia końca zapytania.

Ta własność wynika z zakazu nakładania wpisów. Gdyby dopuścić A[0,100)
i B[10,11), dla zapytania [12,13) najbliższy poprzednik B nie kolidowałby,
ale wcześniejszy A już tak. Sam TreeMap po początkach nie wystarcza do
ogólnego indeksowania dowolnie nakładających się przedziałów.

### Przeniesienie istniejącego wpisu

1. Zweryfikuj ID, wartość i nowy niepusty przedział.
2. Znajdź konflikt z pominięciem wyłącznie tego ID.
3. Przy konflikcie zakończ bez usuwania starego wpisu.
4. Dopiero po pozytywnym sprawdzeniu zastąp zapis w obu indeksach.

Mapa po ID i drzewo po początku muszą wskazywać ten sam zestaw wpisów.
Usunięcie tylko z jednego indeksu dałoby inny wynik odczytu po ID i wyszukiwania
kolizji. Pominięcie własnego ID służy ocenie kandydata; nie wyłącza kontroli
pozostałych rezerwacji tego zasobu.

## Przykład

Wpisy A[10,11) i B[12,13), zapytanie [9,14): pierwszy konflikt to A, pełny wynik to A i B. Przeniesienie A do [11,12) jest poprawne. Próba A[11,13) koliduje z B i pozostawia poprzedni wpis A bez zmian.

| Operacja | Wynik | Stan po operacji |
| --- | --- | --- |
| Zapytanie [11,12) | Brak konfliktu | A i B bez zmian |
| Przeniesienie A do [11,12) | Sukces | A[11,12), B[12,13) |
| Przeniesienie A do [11,13) | Konflikt z B | Nadal A[11,12), B[12,13) |
| Pełna lista dla [9,14) | A i B według początku | Odczyt bez zmiany kalendarza |

Pierwszy konflikt wystarcza do odmowy zapisu. Pełna lista służy wyjaśnieniu
niedostępności i ponosi koszt rozmiaru wyniku K. Niemutowalność zwróconej listy
nie oznacza głębokiego kopiowania dowolnego obiektu V: współdzielony typ wartości
powinien zachowywać swój kontrakt niezmienności.

## Uruchomienie

Java 21, Maven 3.9.9 przez wrapper, JUnit 5.12.2. Z katalogu low-level-design/resource-calendar:

~~~powershell
.\mvnw.cmd --batch-mode --no-transfer-progress clean verify
& "$env:JAVA_HOME/bin/java.exe" -jar target/resource-calendar-1.0.0-SNAPSHOT.jar
& "$env:JAVA_HOME/bin/java.exe" -cp target/resource-calendar-1.0.0-SNAPSHOT.jar pl.jakubtworek.lld.carrental.console.ConsoleMain 2026-10-24
~~~

JAR uruchamia domyślnie konsolę sal. Druga komenda uruchamia wynajem aut z ustaloną datą; jeden build weryfikuje obie części i sam indeks.

## Testy i ograniczenia

[IntervalCalendarTest](src/test/java/pl/jakubtworek/lld/calendar/IntervalCalendarTest.java) porównuje 10000 wygenerowanych operacji z pełnym skanowaniem mapy. Sprawdza sąsiedztwo, pełną listę konfliktów, pomijanie własnego ID, nieudane aktualizacje i niemutowalność wyniku.

Indeks nie obsługuje nakładających się wpisów, rezerwacji cyklicznych ani synchronizacji. Znaczenie czasu, historia i dostępność fizyczna zasobu są rozstrzygane przez obsługę sal lub aut.
