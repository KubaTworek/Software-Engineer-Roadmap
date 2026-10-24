# Low Level Design w Javie

Edukacyjna ścieżka projektowania struktur danych i algorytmów w czystej Javie. Każdy moduł zawiera samodzielną implementację, polskie komentarze, testy i opis działania. Priorytetem są poprawność, koszt operacji, pamięć i utrzymanie indeksów.

## Materiały do nauki

[Książki i źródła do LLD](MATERIALY.md) obejmują kontrakty Javy, struktury danych,
algorytmy, pamięć, snapshoty i testowanie. Każdy z 12 tematów oraz rozgrzewka ma
wskazaną lekturę, dokumentację i pytanie sprawdzające; dostępna jest też ścieżka bezpłatna.

## Projekty

Rozgrzewka: [kółko i krzyżyk](tic-tac-toe/README.md) — plansza N×N/K, maski bitowe i sprawdzanie wyniku.

| # | Projekt | Rdzeń techniczny |
| --- | --- | --- |
| 1 | [Parking](parking-lot/README.md) | Indeks aktywnych biletów i TreeSet wolnych miejsc |
| 2 | [Automat i bankomat](vending-machine/README.md) | Ograniczona reszta, DP i kolejka monotoniczna |
| 3 | [Biblioteka](library/README.md) | Kolejka AVL z rangą i indeks wolnych egzemplarzy |
| 4 | [Kalendarz zasobów](resource-calendar/README.md) | Indeks przedziałów dla rezerwacji sal i aut |
| 5 | [Rozliczenia grupowe](splitwise/README.md) | Przyrostowe salda, dziennik i dokładny podział groszy |
| 6 | [Windy](elevator-system/README.md) | Trwałe FIFO, trie przynależności i symulacja kroków |
| 7 | [Szachy](chess/README.md) | Tablica pól, maski bitowe, legalne ruchy i historia pozycji |
| 8 | [Promocje](promotion-engine/README.md) | Graf konfliktów BitSet i alokacja rabatów |
| 9 | [Historia edytora](editor-history/README.md) | Rope AVL i rozgałęzione undo/redo |
| 10 | [System plików](in-memory-file-system/README.md) | Trwałe indeksy dzieci i kopiowanie ścieżek drzewa |
| 11 | [Cache](in-memory-cache/README.md) | LRU/LFU i indeksowany kopiec TTL |
| 12 | [Logger](logging-library/README.md) | Bufor pierścieniowy, routing i raporty zapisów |

[Archiwum](archive/README.md) zawiera opcjonalne materiały.

## Uruchomienie

Java 21, Maven 3.9.9 przez wrapper i JUnit 5.12.2. Moduły mają osobne buildy; domyślnie działają jednowątkowo i przechowują stan w pamięci, bez usług zewnętrznych.

Z katalogu wybranego modułu:

~~~powershell
.\mvnw.cmd --batch-mode --no-transfer-progress clean verify
~~~

README projektu podaje komendę demo, kontrakty, struktury, złożoność i zakres testów. Koszt aktualizacji jest oddzielony od kosztu snapshotów oraz raportów; analiza złożoności nie zastępuje benchmarku JVM.

## Dokumentacja i praca nad kodem

Obowiązują [AGENTS.md](AGENTS.md), [procedura](PROCEDURE.md) i [standard edukacyjny](EDUCATIONAL_STANDARD.md). [Szablon README](templates/PROJECT_README.md) obejmuje działanie, rdzeń techniczny, przepływy, przykłady, uruchomienie, testy i ograniczenia. Opis jest samodzielny, bez porównań z innymi projektami i omawiania wzorców projektowych.

README wyjaśnia wybór struktur, przebieg algorytmów, zmiany stanu i przypadki
brzegowe, aby po przerwie dało się odtworzyć rozumowanie. Kompaktowanie polega
na łączeniu powtórzeń i drobnych sekcji; nie usuwa uzasadnień ani przykładów.
Komentarze pokazują szczegóły realizacji tych reguł w kodzie.

Głównym tematem opisów są konkretne mechanizmy Javy: działanie kolekcji,
porządek i równość, tablice, boxing, iteratory, kopie, alokacje oraz model
wątkowy. Reguły domenowe stanowią dane do sprawdzania tych mechanizmów.
Część techniczna poprzedza ich szczegółowy opis. Każdy moduł wyjaśnia także
granice jednowątkowości; niezmienny widok nie oznacza automatycznie bezpiecznej
równoległej zmiany właściciela stanu.

Przy przekazywaniu danych między wątkami istotna jest relacja happens-before,
np. zapewniana przez ten sam monitor lub właściwy mechanizm publikacji.
Widoczność referencji i atomowość całej sekwencji sprawdzenie–zapis to różne
gwarancje. Opisy odnoszą je do rzeczywistych pól i indeksów, bez deklarowania
niezaimplementowanej synchronizacji. Podstawę stanowi
[Java Memory Model](https://docs.oracle.com/javase/specs/jls/se21/html/jls-17.html#jls-17.4).

Z katalogu głównego repozytorium:

~~~powershell
pwsh -File scripts/check-documentation.ps1
git diff --check
~~~
