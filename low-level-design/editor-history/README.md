# Historia edytora tekstu

Projekt edukacyjny w czystej Javie: edycja tekstu przez niemutowalny rope oraz undo/redo z zachowaniem rozgałęzień. Stan jest w pamięci i obsługiwany przez jeden wątek.

Materiały do nauki: [lektury i źródła tego ćwiczenia](../MATERIALY.md#historia-edytora).

## Rdzeń techniczny

Rope jest drzewem AVL. Liście mieszczą do 256 jednostek UTF-16, węzły przechowują długość, wysokość i liczbę liści. Dzielenie i łączenie kopiuje zmieniane ścieżki, współdzieląc pozostały tekst. Walidacja zakresu i kontrola nowej długości poprzedzają zmianę historii.

N to długość tekstu, L liczba liści, M długość wstawianego tekstu, B liczba dzieci wersji.

| Operacja | Koszt |
| --- | --- |
| Utworzenie rope / materializacja String | O(N) |
| Odczyt znaku | O(log(L + 1)) |
| Zastąpienie zakresu | O(log(L + 1) + M), przy stałym limicie wielkości liścia |
| Undo | O(1) |
| Redo / checkout | Średnio O(1) dzięki mapie wersji i dzieci |
| Lista gałęzi | O(B), w tym kopia wyniku |

Historia przechowuje węzły wersji i korzenie rope. Nie kopiuje całego tekstu po każdej edycji, ale zachowane gałęzie utrzymują również usunięte fragmenty i węzły dawnych ścieżek. Brak automatycznego przycinania historii.

### Edycja rope na przykładzie

Tekst jest sekwencją liści, a węzeł zna łączną długość lewego i prawego
poddrzewa. Odczyt znaku porównuje indeks z długością lewej części i schodzi
tylko jedną drogą. Nie wyszukuje znaku przez sumowanie długości wszystkich liści.

Zastąpienie zakresu dzieli drzewo przed edytowanym fragmentem i za nim.
Usunięty środek zastępuje rope z wstawianego tekstu, po czym części są łączone.
Join równoważy wysokości AVL i aktualizuje długości w nowych węzłach.
Przykładowo replace(2,2,"XY") dla abcdef daje abXYef; dawna wersja nadal daje abcdef.

Wstawienie M znaków wymaga ich zapisania, stąd składnik O(M). Wyświetlenie
całego tekstu wymaga O(N), nawet jeśli wybór zachowanego korzenia jest stałoczasowy.
Złożoność operacji na drzewie nie obejmuje automatycznie kosztu renderowania.

### Tekst wersji a rosnące drzewo historii

Snapshot przechowuje tekst i ID wersji, ale nie żywą listę jej dzieci.
Tekst jest stały; do tej samej wersji mogą później zostać dopisane nowe gałęzie.
branches() tworzy więc nową, niemutowalną listę opisów dzieci przy odczycie.

Wersja oczekiwana chroni intencję edycji. Nie jest blokadą ani operacją CAS,
więc nie zapewnia bezpiecznego dostępu wielu wątków. Walidacja wersji i zakresu
oraz utworzenie nowego rope i ID poprzedzają dodanie węzła do historii.
Gwarancja odmowy obejmuje te sprawdzenia; nie jest mechanizmem rollback po OOM.

## Mechanizmy Javy i model wątkowy

### CharSequence, String i współdzielenie pamięci

RopeText implementuje CharSequence, więc udostępnia length, charAt
i subSequence bez obowiązku spłaszczania do String. subSequence zwraca
kolejny rope. Wywołanie toString musi jednak przejść po całym tekście
i zbudować wynik; interfejs nie zmienia tego kosztu.

Liście przechowują niemutowalne String, a węzły finalne długości i odnośniki.
Nie wystawiamy mutowalnej tablicy char[]. Indeksy nadal dotyczą jednostek
UTF-16: emoji poza BMP może zajmować dwa char. Długość w znakach widocznych
nie jest więc równa length, a granica edycji może przeciąć parę surogatów.

Współdzielenie dotyczy niezmienionych węzłów rope. Dzielenie liścia korzysta
z substring; nie zakładamy, że każdy taki fragment jest darmowym widokiem
jednej wspólnej tablicy znaków. Rozmiar liścia ogranicza pracę lokalną,
a usunięty tekst pozostaje osiągalny przez korzenie zachowanych wersji.

### HashMap i LinkedHashMap mają różne zadania

`HashMap<Long, Version>` wyszukuje wersję po numerze; long jest tu opakowywany
w Long. LinkedHashMap dzieci zachowuje kolejność ich dodawania, potrzebną
do przewidywalnego branches(). Preferowane redo jest osobną referencją,
nie wynikiem „ostatniego elementu dowolnej mapy”.

Version nie jest niemutowalnym drzewem: ma dopisywane children i zmieniane
preferred. Niemutowalny jest tekst snapshotu. Final przy referencji children
nie zabrania put; branches() musi zwracać osobną listę opisów.

### ExpectedVersion i Java Memory Model

Sprawdzenie expectedVersion chroni przed zastosowaniem edycji do niewłaściwego
widoku, ale porównanie i zapis są oddzielnymi krokami. Nie ma CAS ani blokady.
Dwa wątki mogłyby przejść kontrolę tej samej wersji i zmienić indeks oraz kursor.

Niemutowalny tekst można rozpatrywać oddzielnie od dostępu do EditorHistory.
Współdzielenie właściciela, list dzieci i preferencji wymagałoby osobnego
kontraktu synchronizacji. Taki mechanizm nie jest tutaj zaimplementowany.
API tekstu: [CharSequence](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/CharSequence.html).

## Zakres, reguły i niezmienniki

- Jeden wątek, pamięć procesu, bez plików, sieci, ról i Clock.
- Początkowa wersja ma ID 0. Nowe ID rosną i nie są ponownie używane po undo.
- Zakresy używają jednostek UTF-16, jak String. Można rozdzielić parę surogatów;
  nie implementujemy nawigacji po grafemach ani normalizacji Unicode.
- Każda edycja wskazuje aktualną expectedVersion. Nieaktualna wersja daje
  IllegalStateException i nie zmienia kursora, tekstu ani listy gałęzi.
- Zakres musi spełniać 0≤offset≤N oraz 0≤removed≤N−offset.
- Pusta wstawka usuwająca zero znaków jest brakiem zmiany. Zastąpienie niepustego
  zakresu identycznym tekstem tworzy wersję: nie porównujemy całego dokumentu.
- Undo na korzeniu i redo bez dziecka dają NoSuchElementException.
  Nieznane checkout i redo spoza bezpośrednich dzieci dają IllegalArgumentException.
- Nowa edycja po undo zachowuje poprzednie dzieci. Domyślne redo wybiera ostatnio
  odwiedzone lub utworzone dziecko; redo(id) wybiera konkretną bezpośrednią gałąź.
- Checkout jest skokiem, nie odtwarzaniem całej ścieżki. Nie przepisuje preferencji
  wszystkich przodków. Undo po skoku prowadzi do rodzica wybranej wersji.
- Snapshot przechowuje wyłącznie niezmienny tekst i ID. Lista gałęzi jest osobnym,
  aktualnym zapytaniem zwracającym stabilną kopię.

## Drzewo wersji i przebieg edycji

Przykład: początek A → insert(1,"B") daje v1=AB → insert(2,"C") daje v2=ABC.
Undo wraca do v1. Insert(2,"X") tworzy v3=ABX z rodzicem v1, zachowując v2.
Undo i branches zwracają dzieci [v2,v3]. Redo(2) przywraca ABC bez przeliczania
wstawek. Dawny snapshot v3 nadal daje ABX.

Edycja najpierw sprawdza wersję i zakres, następnie przygotowuje kompletny rope
oraz ID. Dopiero potem dopisuje węzeł do indeksu i dzieci oraz zmienia kursor.
Gwarancja odmowy dotyczy walidacji i obliczeń; nie obiecujemy rollback po OOM.

## Przykład

Tekst A → AB (v1) → ABC (v2). Undo wraca do AB; dopisanie X tworzy ABX (v3). Kolejne undo pozwala wybrać redo 2 i otrzymać ABC albo redo 3 i otrzymać ABX. Snapshot v2 pozostaje niezmieniony.

## Uruchomienie

Java 21; z katalogu low-level-design/editor-history:

~~~powershell
.\mvnw.cmd --batch-mode --no-transfer-progress clean verify
& "$env:JAVA_HOME/bin/java.exe" -jar target/editor-history-1.0.0-SNAPSHOT.jar
~~~

Polecenia: `insert OFFSET TEXT`, `delete OFFSET LENGTH`, `undo`, `redo [ID]`, `checkout ID`, `branches`, `show`, `q`. Konsola przycina białe znaki na krańcach całego wiersza; pełny tekst z takimi znakami i nowymi liniami można przekazać przez API.

## Testy i weryfikacja

[Testy](src/test/java/pl/jakubtworek/lld/editor) porównują 7000 operacji rope z StringBuilder i 8000 operacji historii z niezależnym drzewem wersji. Sprawdzają granice bloków 255/256/257, stare korzenie, zakresy, brak częściowych zmian oraz wysokość po 10000 dopisaniach.

### Co sprawdzają testy

| Obszar | Test i sprawdzana własność |
| --- | --- |
| Operacje na tekście | [RopeTextTest](src/test/java/pl/jakubtworek/lld/editor/RopeTextTest.java): porównanie odczytów, edycji i fragmentów z StringBuilder |
| Zrównoważenie | Ten sam plik: wysokość po długiej serii dopisań i granice bloków; zgodny tekst bez tej kontroli nie dowodzi zachowania kosztu |
| Historia | [EditorHistoryTest](src/test/java/pl/jakubtworek/lld/editor/EditorHistoryTest.java): rodzice, dzieci, preferowane redo, checkout i odmowy względem osobnego modelu |
| Starsze widoki | Zachowane teksty i snapshoty nie zmieniają się po edycji innej gałęzi |
| Interfejs tekstowy | [ConsoleMainTest](src/test/java/pl/jakubtworek/lld/editor/console/ConsoleMainTest.java): przebieg rozmowy i komunikaty błędów |

Testy generowane mają ustalone ziarna i sprawdzają próbkę historii. Kontrola
wysokości chroni przed degeneracją drzewa; nie jest pomiarem czasu pracy JVM.
Zachowanie wszystkich gałęzi jest jawne: samo undo nie zwalnia pamięci tekstu.

## Ograniczenia

Brak plików, współpracy użytkowników, synchronizacji i interpretowania indeksów jako znaków widocznych dla użytkownika. versionCount obejmuje wszystkie zachowane gałęzie.
