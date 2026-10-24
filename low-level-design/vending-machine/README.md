# Automat sprzedażowy i bankomat

Projekt edukacyjny w czystej Javie: wydawanie dokładnej kwoty z ograniczonego zapasu nominałów. Implementacja zawiera automat sprzedażowy i bankomat, własne reguły obu urządzeń oraz jeden planer obliczeniowy. Stan znajduje się w pamięci; obsługa jest jednowątkowa.

## Rdzeń techniczny

[BoundedChangePlanner](src/main/java/pl/jakubtworek/lld/change/BoundedChangePlanner.java) przyjmuje tablice wartości nominałów i dostępnych ilości oraz kwotę docelową. Wartości muszą być dodatnie, ilości i kwota nieujemne, tablice równych długości. Nominały nie muszą być uporządkowane i mogą się powtarzać. Wejściowe tablice pozostają bez zmian.

Dla D rodzajów nominałów i kwoty A programowanie dynamiczne wyznacza minimalną liczbę sztuk. Przetwarzanie jednego nominału w dzieli kwoty według reszty r z dzielenia przez w:

~~~text
next[r + t*w] = t + min(best[r + j*w] - j)
               dla j w [t - dostępnaLiczba, t]
~~~

Kolejka monotoniczna w tablicy int przechowuje kandydatów minimum. Każdy indeks jest dodawany i usuwany najwyżej raz. Osobne warstwy best i next uniemożliwiają ponowne wykorzystanie zapasu z tego samego kroku; zapamiętane decyzje pozwalają odtworzyć zestaw nominałów.

| Operacja | Czas | Pamięć |
| --- | --- | --- |
| Obliczenie i odtworzenie planu | O(D × A) | O(D × A), w tym decyzje odtwarzania |
| Wynik | O(D) | Nowa tablica liczności |
| Kwota zero | Zwraca same zera | Bez zużycia zapasu |
| Kwota nieosiągalna | Optional.empty | Bez zmiany zapasu urządzenia |

Remisy są rozstrzygane deterministycznie przez kolejność poprzedników. Minimalizacja dotyczy liczby sztuk bieżącej wypłaty; nie przewiduje przyszłego zapotrzebowania. Wydanie jest zatwierdzane przez urządzenie dopiero po walidacji kompletnego planu.

## Mechanizmy Javy i model wątkowy

### Tablice prymitywne zamiast kolekcji stanów DP

Kwota jest gęstym indeksem od 0 do A. int[] pozwala odczytać stan bez hashCode,
węzła mapy i opakowania Integer. Domyślne zera nie oznaczają nieosiągalności:
Arrays.fill ustawia best na A + 1, a best[0] na zero. Ponieważ najmniejszy
nominał jest dodatnią liczbą całkowitą, osiągalny wynik nie potrzebuje więcej
niż A sztuk. Wartość A + 1 jest więc odróżnialnym znacznikiem.

best, next i deque są osobnymi tablicami. Instrukcje int[] swap = best;
best = next; next = swap zamieniają referencje, nie kopiują A komórek.
Przed kolejną warstwą next jest ponownie wypełniana. Przypisanie best = next
bez zachowania drugiego bufora spowodowałoby aliasowanie warstw.

int[][] taken jest w Javie tablicą referencji do osobnych int[], a nie jedną
ciągłą macierzą. Kod alokuje wiersz tylko dla nominału z użytecznym zapasem.
To ogranicza alokacje dla pominiętych warstw, ale nadal zachowuje koszt
rekonstrukcji O(D × A) w najgorszym przypadku. Kolejka monotoniczna w int[]
używa indeksów head/tail; `ArrayDeque<Integer>` wymagałaby boxingu indeksów.

`Optional<int[]>` sygnalizuje brak rozwiązania, lecz nie czyni wyniku
niemutowalnym. Zwrócona tablica jest nowym wynikiem należącym do wywołującego;
jej zmiana nie modyfikuje planera. Tablice wejściowe nie są przechowywane,
ale nie wolno zmieniać ich równolegle podczas wykonywania plan.

### Wątki i własność pamięci

Planer trzyma tylko finalny limit; bufory każdego wywołania są lokalne.
Współdzielenie planera i współdzielenie tablic klienta to zatem różne kwestie.
Wykonanie obliczenia nie blokuje zasobów urządzenia: między odczytem zapasu
a zastosowaniem planu inny zapis mógłby go zmienić. Oba urządzenia zachowują
kontrakt jednowątkowy; sam lokalny DP nie synchronizuje kont ani kasy.

Znaczenie tablic jako obiektów opisuje [JLS: Arrays](https://docs.oracle.com/javase/specs/jls/se21/html/jls-10.html).

## Zakres

| Część | Zachowanie |
| --- | --- |
| [Automat sprzedażowy](VENDING.md) | Wybór produktu, depozyt monet, zakup, reszta, anulowanie i uzupełnianie zapasów |
| [Bankomat](ATM.md) | Sesja karty, PIN, termin ważności, wypłaty i ponowienia z kluczem operacji |
| Planer kwoty | Znalezienie dostępnej kombinacji o najmniejszej liczbie monet lub banknotów |

Kwoty są całkowitymi groszami PLN, bez obliczeń zmiennoprzecinkowych. Automat obsługuje resztę do 10000 gr. Bankomat wypłaca do 2000 PLN, a jego planer operuje w jednostkach 10 PLN.

## Jak działa obliczenie planu

Stan DP dla kwoty a oznacza najmniejszą liczbę sztuk potrzebnych do jej wydania
z już przetworzonych rodzajów nominałów. Stan początkowy to 0 dla kwoty zero
i nieosiągalność dla pozostałych kwot. Każda kolejna warstwa dopuszcza ograniczoną
liczbę sztuk jednego nowego nominału.

Dla nominału w i kwoty r + t*w wybór poprzednika j oznacza użycie t − j sztuk.
Warunek 0 ≤ t − j ≤ limit daje okno j od max(0, t − limit) do t.
Koszt best[r + j*w] + t − j można rozdzielić na stałe t i minimum best − j.
Właśnie to minimum utrzymuje kolejka monotoniczna.

Kandydat starszy niż dolna granica wypada z początku. Z końca usuwane są
zdominowane koszty: późniejszy kandydat o mniejszym koszcie pozostanie ważny
co najmniej tak długo jak wcześniejszy i nie da gorszego wyniku. Do kolejki
trafiają wyłącznie osiągalne stany. Każdy indeks pojawia się i znika najwyżej raz,
co usuwa dodatkową pętlę po liczbie dostępnych monet.

Warstwy muszą pozostać rozdzielone. Użycie właśnie obliczonego next jako
poprzedniego best pozwoliłoby zużywać ten sam ograniczony zapas wielokrotnie.
Po obliczeniu celu planer przechodzi wstecz przez zapisane liczności,
odejmując wartość wybranych monet. Zwraca konkretną tablicę planu, a nie samo true.

### Prześledzony przykład

Do wydania jest 6 jednostek. Dostępne są nominały 1 × 1, 3 × 2, 4 × 1.

| Przetworzone nominały | Osiągalne kwoty do 6 | Minimum dla 6 |
| --- | --- | --- |
| Żaden | 0 | Brak rozwiązania |
| 1, jedna sztuka | 0, 1 | Brak rozwiązania |
| Dodatkowo 3, dwie sztuki | 0, 1, 3, 4, 6 | 2 sztuki: 3 + 3 |
| Dodatkowo 4, jedna sztuka | 0, 1, 3, 4, 5, 6 | Nadal 2 sztuki: 3 + 3 |

## Wykonalność, minimum i zapas

Dostępne są: jedna sztuka nominału 1, dwie sztuki nominału 3 i jedna sztuka nominału 4. Dla kwoty 6 wybór największego nominału pozostawia niewydawalne 2. Planer znajduje 3 + 3. Po ograniczeniu zapasu nominału 3 do jednej sztuki ta sama kwota staje się nieosiągalna.

Wartość całego zapasu jest tylko warunkiem koniecznym. Nie dowodzi wykonalności
dokładnego wydania. Z kolei dowolny wykonalny plan nie musi być minimalny:
z zasobu 50,20,20,20,10 kwotę 60 można wydać jako 20+20+20 albo 50+10.
W obu przypadkach bilans się zgadza, lecz drugie rozwiązanie wydaje mniej sztuk.

Urządzenie przelicza swoje jednostki przed wywołaniem planera i stosuje wynik
dopiero po walidacji. Planer nie zmienia tablic zapasu, salda ani sesji.
To rozdziela koszt obliczenia od aktualizacji stanu i pozwala sprawdzać algorytm
na małych, w pełni enumerowanych danych.

## Uruchomienie

Java 21, Maven 3.9.9 przez wrapper, JUnit 5.12.2. Z katalogu low-level-design/vending-machine:

~~~powershell
.\mvnw.cmd --batch-mode --no-transfer-progress clean verify
& "$env:JAVA_HOME/bin/java.exe" -jar target/vending-machine-1.0.0-SNAPSHOT.jar
& "$env:JAVA_HOME/bin/java.exe" -cp target/vending-machine-1.0.0-SNAPSHOT.jar pl.jakubtworek.lld.atm.console.ConsoleMain
~~~

Jeden build uruchamia testy planera oraz obu urządzeń. Parametry i polecenia konsol opisują dokumenty poszczególnych części.

## Testy i ograniczenia

[BoundedChangePlannerTest](src/test/java/pl/jakubtworek/lld/change/BoundedChangePlannerTest.java) porównuje 3000 małych wygenerowanych przypadków z pełną enumeracją, także dla nieuporządkowanych i powtarzających się nominałów. Testy urządzeń sprawdzają zgodność planu z saldem, zapasem i historią oraz odmowy bez częściowych zmian.

Limity kwot ograniczają rozmiar tablic DP. Brak obsługi sprzętu, trwałego zapisu i współbieżnych transakcji.
