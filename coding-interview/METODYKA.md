# Jak rozwiązywać zadania coding interview

[Mapa modułu](README.md) · [Java i koszty](JAVA.md) · [Plan ćwiczeń](PLAN.md)

Dobór techniki po przeczytaniu treści rozwija [przewodnik wyboru algorytmu](PRZEWODNIK.md).
Do własnych prób służy [bank 72 zadań ze szkieletami i testami](exercises/README.md)
oraz [trening mieszany](practice/README.md), który ukrywa kategorię.

## 1. Najpierw zapisz kontrakt

Ustal wejście, wynik i ograniczenia. Czy trzeba zwrócić indeksy, wartości, liczbę
wyników czy wszystkie rozwiązania? Czy dane są posortowane? Czy występują liczby
ujemne, duplikaty, puste wejście? Czy można zmienić tablicę? Co oznacza brak wyniku?
`Subarray` jest spójny, `subsequence` zachowuje kolejność, a podzbiór nie musi jej
zachowywać. Ta różnica często zmienia cały algorytm.

Dla Two Sum zapisz np.: nieposortowane `int[]`, dwa różne indeksy, suma liczona
bez przepełnienia `int`, dowolna poprawna para, pusta tablica wyniku przy braku
pary. To kontrakt naszej karty; zadanie platformy może gwarantować jedną parę.

## 2. Zbuduj prosty punkt odniesienia

Nazwij przestrzeń kandydatów. Dla pary sprawdź wszystkie `i < j`, dla spójnego
fragmentu wszystkie początki i końce, dla podzbiorów decyzję „biorę / pomijam”.
Brute force daje poprawny punkt odniesienia i pomaga później porównywać wyniki.

Policz operacje w zależności od rozmiaru. Wszystkie pary to O(n²), sortowanie
porównaniowe to O(n log n), wszystkie podzbiory to 2ⁿ wyników. Ograniczenia
zawężają kandydatów na algorytm, ale sam rozmiar danych nie dowodzi poprawności.

## 3. Znajdź pracę, którą można usunąć

| Pytanie | Możliwa technika | Co trzeba udowodnić |
| --- | --- | --- |
| Czy ciągle szukam wartości widzianej wcześniej? | hash map | stan mapy odnosi się do właściwych indeksów |
| Czy porządek pozwala odrzucić cały zbiór par? | two pointers | odrzucane pary nie mogą dać rozwiązania |
| Czy mogę rozszerzać i naprawiać spójne okno? | sliding window | przesunięcie lewej granicy nie pomija optimum |
| Czy wynik zakresu jest różnicą prefiksów? | prefix sum | prefiks początkowy i kolejność aktualizacji są poprawne |
| Czy warunek zmienia się tylko raz? | binary search | istnieje monotoniczny podział fałsz / prawda |
| Czy te same stany wracają w rekurencji? | DP | stan zawiera całą informację potrzebną do dalszej decyzji |
| Czy muszę wypisać wszystkie możliwości? | backtracking | każda gałąź jest kompletna i stan jest cofany |

Nie dopasowuj nazwy po jednym słowie. „Najkrótszy fragment” nie gwarantuje sliding
window: ujemne liczby mogą zniszczyć monotoniczność sumy. „Najkrótsza ścieżka” nie
gwarantuje BFS: różne wagi krawędzi wymagają innego uzasadnienia i algorytmu.

## 4. Sformułuj niezmiennik i argument poprawności

Niezmiennik opisuje stan w konkretnym miejscu pętli lub rekurencji. Dla Two Sum:
przed obsługą indeksu `i` mapa zawiera tylko wartości z indeksów mniejszych niż `i`.
Dlatego odczyt dopełnienia przed zapisem bieżącej wartości nie użyje tego samego
elementu dwa razy. Para zostanie znaleziona przy swoim późniejszym indeksie.

Sprawdź inicjalizację, zachowanie po kroku oraz wniosek przy zakończeniu. Dodaj
miarę postępu: rosnący indeks, malejący przedział albo liczbę nieodwiedzonych
wierzchołków. Poprawność wyniku i zakończenie to osobne obowiązki.

## 5. Prześledź dane przed pisaniem kodu

Przykład Two Sum: `[3, 2, 4]`, cel `6`.

| i | Wartość | Dopełnienie | Mapa przed krokiem | Decyzja |
| --- | ---: | ---: | --- | --- |
| 0 | 3 | 3 | pusta | zapisz 3 → 0 |
| 1 | 2 | 4 | 3 → 0 | zapisz 2 → 1 |
| 2 | 4 | 2 | 3 → 0, 2 → 1 | zwróć [1, 2] |

Powtórz na `[3]` i `[3, 3]`. Te dwa wejścia ujawniają błąd polegający na
zapisaniu bieżącego elementu przed odczytem dopełnienia.

## 6. Zapisz kod i sprawdź granice

Trzymaj jeden kontrakt zakresów: np. `[left, right)` w binary search. Dobierz
typ sumy przed obliczeniem, a nie dopiero przy przypisaniu. Nazwij stan zgodnie
z rolą: `indegree`, `prefix`, `distance`, `lastSeen`, zamiast jednej litery na
każdą pomocniczą strukturę. Nie dodawaj warstw aplikacyjnych do krótkiego zadania.

Sprawdź puste wejście, jeden element, duplikaty, brak odpowiedzi, odpowiedź przy
pierwszym lub ostatnim elemencie i granice typów. W grafie dodaj cykl oraz osobną
składową. W rekurencji policz głębokość stosu. Porównuj z brute force na małych
danych, jeżeli trudniej ręcznie ustalić wynik.

## 7. Wyjaśnij koszt i wariant

Powiedz, co oznacza `n`, ile razy każdy element trafia do struktury i ile pamięci
zajmują indeksy, rekurencja oraz wynik. Dwie zagnieżdżone pętle nie muszą oznaczać
O(n²): w monotonic stack każdy indeks jest wkładany i zdejmowany najwyżej raz.

Na koniec zmień jeden warunek. Czy Two Sum nadal ma zwracać indeksy po sortowaniu?
Czy okno działa po dodaniu liczb ujemnych? Czy wybór najwcześniejszego końca
spotkania maksymalizuje również sumę jego wartości? Umiejętność pokazania granicy
techniki jest częścią rozwiązania.

## Organizacja próby i powtórki

Przykładowa próba 40 minut: 5 minut na kontrakt, 8 na brute force i obserwację,
7 na uzasadnienie, 15 na kod, 5 na przypadki brzegowe i koszt. To propozycja
treningowa, nie reguła firm. Na rozmowie komunikuj założenia i dostosuj tempo.

Po zadaniu zapisz: własną błędną hipotezę, minimalny kontrprzykład, właściwy
niezmiennik i warunek użycia techniki. Wróć do zadania po kilku dniach i połącz
je z innym schematem. Odtworzenie kodu z pamięci nie zastępuje wyjaśnienia decyzji.
