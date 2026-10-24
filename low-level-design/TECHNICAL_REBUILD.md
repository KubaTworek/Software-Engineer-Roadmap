# Reorganizacja LLD — 12 tematów i rozgrzewka

Aktualny program to **12 głównych tematów technicznych**, osobna rozgrzewka
oraz opcjonalne archiwum. Indeks: [README LLD](README.md).

Wcześniejsza lista 15 ćwiczeń powtarzała dobór ograniczonej reszty oraz kalendarze.
Połączenie dotyczy źródeł, algorytmów, testów i buildów, nie tylko nagłówków README.

## Aktualne moduły i weryfikacja

| # | Temat | Rdzeń | Testy / clean verify |
| --- | --- | --- | --- |
| 1 | [Indeksowanie zasobów — parking](parking-lot/README.md) | HashMap aktywnych wpisów, TreeSet wolnych miejsc | 99 / BUILD SUCCESS |
| 2 | [Ograniczona reszta — automat i bankomat](vending-machine/README.md) | DP i kolejka monotoniczna; jeden planer dla dwóch adapterów | 103 / BUILD SUCCESS |
| 3 | [Kolejka z rangą — biblioteka](library/README.md) | AVL z rozmiarami poddrzew i indeks wolnych egzemplarzy | 49 / BUILD SUCCESS |
| 4 | [Kalendarz zasobów — sale i auta](resource-calendar/README.md) | Jeden indeks przedziałów, pierwszy konflikt i pełny raport | 96 / BUILD SUCCESS |
| 5 | [Przyrostowe rozliczenia — Splitwise](splitwise/README.md) | Tablica long[], delty, indeks dziennika i dokładne grosze | 52 / BUILD SUCCESS |
| 6 | [Kolejki i symulacja — windy](elevator-system/README.md) | Trwałe FIFO, trie członkostwa, estymacja kroków | 39 / BUILD SUCCESS |
| 7 | [Reprezentacja pozycji — szachy](chess/README.md) | Tablica 64 pól, maski bitowe, generator i odwracalne delty | 149 / BUILD SUCCESS |
| 8 | [Graf konfliktów — promocje](promotion-engine/README.md) | BitSet, priorytety i dokładna alokacja groszy | 81 / BUILD SUCCESS |
| 9 | [Historia edytora](editor-history/README.md) | Rope AVL, współdzielenie tekstu, undo/redo i drzewo wersji | 18 / BUILD SUCCESS |
| 10 | [Hierarchiczne dane — system plików](in-memory-file-system/README.md) | Trwałe AVL dzieci, kopiowanie drogi i przejścia drzewa | 74 / BUILD SUCCESS |
| 11 | [Cache w pamięci](in-memory-cache/README.md) | LRU/LFU, kubełki częstotliwości i indeksowany kopiec TTL | 50 / BUILD SUCCESS |
| 12 | [Buforowanie i routing — logger](logging-library/README.md) | Pierścień rekordów, maski tras i koszt pełnego raportu | 94 / BUILD SUCCESS |

Rozgrzewka [tic-tac-toe](tic-tac-toe/README.md): 82 testy.
Opcjonalne [archiwum workflow](archive/document-workflow/README.md): 54 testy,
poza numeracją i zakresem obowiązkowym.

Wykonano z katalogu każdego z 12 tematów, rozgrzewki i archiwum:

~~~powershell
.\mvnw.cmd --batch-mode --no-transfer-progress clean verify
~~~

**986 testów aktywnej ścieżki z rozgrzewką, wszystkie poprawne.
Archiwum: dodatkowe 54 poprawne testy. Brak błędów i pominięć.**
Środowisko: JDK 23.0.2, kompilacja --release 21. Nie zmieniano zależności
ani wersji narzędzi; nie wykonywano osobnego przebiegu na JDK 21.

## Co połączono w kodzie

### Ograniczona reszta — temat 2

Źródła i testy bankomatu przeniesiono do vending-machine. Nie istnieje drugi
aktywny pom bankomatu. MinimumCoinChangePlanner i BoundedCashPlanner są
adapterami jednego [BoundedChangePlanner](vending-machine/src/main/java/pl/jakubtworek/lld/change/BoundedChangePlanner.java).
Monety używają grosza, banknoty jednostki 10 PLN. Nie dublujemy kolejki
monotonicznej i rekonstrukcji. Zachowano testy obu domen i dodano enumeracyjną
wyrocznię wspólnego rdzenia. Przewodniki: [automat](vending-machine/VENDING.md)
i [bankomat](vending-machine/ATM.md).

### Kalendarz zasobów — temat 4

Auta i sale mają jeden pom, src/main i src/test w resource-calendar.
Adaptery wywołują [IntervalCalendar](resource-calendar/src/main/java/pl/jakubtworek/lld/calendar/IntervalCalendar.java),
który indeksuje rozłączne przedziały TreeMap i mapą ID. Różnicą zapytania jest
pierwszy konflikt albo pełna lista. Zachowano LocalDate oraz Instant,
priorytet umowy/serwisu, bufor sal i indeks fizycznego wydania. Test wspólnego
indeksu porównuje 10000 operacji ze skanem. Przewodniki:
[sale](resource-calendar/MEETING_ROOMS.md) i [auta](resource-calendar/CAR_RENTAL.md).

### Historia edytora — temat 9

To nowa implementacja, a nie przemianowany workflow. [RopeText](editor-history/src/main/java/pl/jakubtworek/lld/editor/RopeText.java)
przechowuje tekst w niezmiennym AVL fragmentów. Edycja używa split/join
i współdzieli niezmienione gałęzie; renderowanie tekstu pozostaje jawnym O(N).
[EditorHistory](editor-history/src/main/java/pl/jakubtworek/lld/editor/EditorHistory.java)
przechowuje drzewo wersji, rodziców, dzieci, preferowane redo i indeks ID.

Edycja po undo zachowuje dawną gałąź. Undo/redo/checkout wybierają korzeń,
bez odtwarzania operacji tekstowych. Testy porównują 7000 edycji z StringBuilder,
8000 poleceń z modelem pełnych tekstów i sprawdzają równoważenie po 10000 dopisań.
Brak ról, recenzentów, kampanii i infrastruktury w nowym rdzeniu.

Dawny model akceptacji z testami pozostaje w archive/document-workflow.
Archiwum nie jest zależnością historii edytora ani kolejnych tematów.

## Mapa dawnych ścieżek

| Dawna ścieżka | Aktualny materiał |
| --- | --- |
| atm | vending-machine/ATM.md |
| car-rental | resource-calendar/CAR_RENTAL.md |
| meeting-room-booking | resource-calendar/MEETING_ROOMS.md |
| document-workflow | editor-history/README.md; poprzedni kod w archive/document-workflow |
| tic-tac-toe jako projekt 1 | rozgrzewka bez numeru w głównej ścieżce |

Dawne adresy mają małe README z przekierowaniem. Ich katalogi nie zawierają
aktywnych źródeł ani pomów. Numery tematów zostały zaktualizowane w dokumentacji.

## Jak oceniać efektywność i dalsze projekty

Złożoność wynika z liczby odwiedzanych węzłów, stanów DP lub elementów wyniku.
Testy różnicowe potwierdzają poprawność w granicach danych; nie są benchmarkiem.
Nie deklarujemy najszybszego rozwiązania dla każdego obciążenia.

Nowy główny temat ma wnosić odrębny problem techniczny. Zmiana nazwy domeny
lub jednostki danych powinna najpierw stać się wariantem istniejącego modułu.
Koszt snapshotu, raportu, pamięci indeksów, boxingu i retencji starych wersji
opisujemy osobno od pojedynczej aktualizacji.

Stosuj [AGENTS.md](AGENTS.md), [procedurę](PROCEDURE.md),
[standard edukacyjny](EDUCATIONAL_STANDARD.md) i [szablon README](templates/PROJECT_README.md).
