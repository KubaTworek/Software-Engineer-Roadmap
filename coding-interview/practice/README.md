# Trening mieszany bez nazwy kategorii

[Mapa](../README.md) · [Zasady treningu](../NAUKA.md)

Cztery zestawy po sześć zadań obejmują łącznie wszystkie 24 kategorie. Numery
P01–P06 są lokalne dla pliku, nie oznaczają techniki. Źródło z kategorią i trzema
wskazówkami znajduje się dopiero w rozwijanej sekcji pod zadaniem.

- [Zestaw 1](01-session.md)
- [Zestaw 2](02-session.md)
- [Zestaw 3](03-session.md)
- [Zestaw 4](04-session.md)

Każdy zestaw to niezależna kopia robocza treści, szkieletów i testów. Rozwiązanie
w nim nie zmienia oryginału w exercises/ ani odwrotnie. Wymagania wspólne dla
kategorii zostały skopiowane przy zadaniu; nie zgaduj brakujących warunków.
Przed kodem zapisz dwie możliwe techniki i uzasadnij odrzucenie jednej.

Z katalogu coding-interview:

```powershell
pwsh -File scripts/check-exercises.ps1 -PracticeFile practice/01-session.md -Exercise P01
pwsh -File scripts/check-exercises.ps1 -PracticeFile practice/01-session.md -RunSolved

# Kolejna próba z neutralnymi ID; wynik jest zwykłym Markdown do edycji.
pwsh -File scripts/new-practice.ps1 -Count 6 -Seed 2026 -OutputPath practice/my-session.md

# Trzy próby L1–L2 z poznanych kategorii, bez wskazanego zadania.
pwsh -File scripts/new-practice.ps1 -Count 3 -Seed 17 -Level 'L1,L2' -Categories 'hash-map,two-pointers' -ExcludeIds HASH-01 -ExcludeSolved -OutputPath practice/foundations.md

# Kontrola treści i testów kopii względem źródła, bez zapisu plików.
pwsh -File scripts/check-practice.ps1
```

Można wskazać -JdkHome przy kontroli. To samo ziarno i ten sam bank dają ten sam
dobór zadań. Zmiana banku może zmienić dobór. Nie używaj -Force dla zestawu,
w którym masz własną pracę; generator domyślnie odmawia nadpisania pliku.
Nie uruchamiaj generatora w CI nad edytowanymi sesjami — CI tylko je czyta.

## Filtry i wykluczenia

- -Level przyjmuje L1, L2, L3; można użyć listy rozdzielonej przecinkami.
- -Categories (alias -KnownCategories) przyjmuje nazwy plików kategorii bez
  numeru i rozszerzenia, np. hash-map, two-pointers, dijkstra.
- -ExcludeIds wyklucza konkretne oryginalne ID. -ExcludeFile czyta plik tekstowy:
  jedno ID na linię, puste linie i komentarze po # są pomijane.
- -ExcludeSolved pomija w banku metody bez TODO. Nie wnioskuje o zaliczeniu testów.
- -Ids pozwala jawnie ustalić pulę; wskazane ID muszą spełniać pozostałe filtry.

Nieznany poziom, kategoria lub ID oraz za mała pula powodują błąd przed zapisem.
Kategorie filtrują materiał, a ich nazwy nadal pozostają ukryte w wynikowej próbie.

## Zgodność kopii i ochrona pracy

W rozwijanej sekcji są niewidoczne w renderowanym tekście oznaczenia origin
i snapshot. Skróty SHA-256 dotyczą źródłowego kontraktu (z poziomem i wskazówkami),
testów i szkieletu oraz wyrenderowanej treści próby. Normalizujemy końce linii
i końcowe białe znaki; nie pomijamy merytorycznych zmian kodu.

check-practice.ps1 raportuje osobno SOURCE_* (zmiana źródła), LOCAL_* (lokalna
zmiana treści, testów lub niedokończonego szkieletu) i brak pochodzenia. Samo
uzupełnienie rozwiązania jest dozwolone i nie jest rozjazdem. Kontrola niczego
nie zapisuje; przy rozjeździe kończy się błędem i wskazuje plik z identyfikatorem.

Dodane przez ciebie testy również zostaną zgłoszone jako LOCAL_TESTS_CHANGED:
to sygnał do ręcznego porównania, nie polecenie usunięcia asercji. Zachowaj taką
kopię i uruchamiaj jej testy przez -PracticeFile. Nowy zgodny zestaw przygotuj pod
inną nazwą; nie przepisuj skrótów tylko po to, żeby uciszyć kontrolę.

-Force odświeża wyłącznie nietknięty szkielet z niezmienionymi lokalnymi testami
i treścią. Odmawia nadpisania rozwiązania, zmienionego szkieletu lub własnych
testów. Wybierz wtedy nową nazwę. Wbudowane zestawy są sprawdzane w CI pod kątem
pochodzenia i kompilacji. Filtry oraz odmowę nadpisania sprawdza test-practice.ps1.

Wskazówki są ukryte w renderowanym Markdown; w surowym źródle można zobaczyć
odnośnik. To pomoc w samodzielnej nauce, nie zabezpieczenie egzaminacyjne.
