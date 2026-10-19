# modeling

<!-- material-card:start -->
> [!IMPORTANT]
> **Karta materiału**
> - **Zakres:** `fundament`
> - **Uczy:** modeling.
> - **Typowy błąd:** Projekt klucza, który wymusza pełny skan dla głównego zapytania.
> - **Najkrótsza weryfikacja:** `.\mvnw.cmd --batch-mode --no-transfer-progress "-Dtest=AccessPatternDesignTest" test`
> - **Katalog uruchomienia:** `backend-engineering` (katalog głównego modułu).
> - **Role klas:** brak jawnej roli w nazwach klas; intencję przykładu określają opis i test, nie sam brak prefiksu.
> - **Granica:** Model i H2 sprawdzają logikę; blokady, plan zapytania i zachowanie silnika potwierdza dopiero odpowiedni profil infrastrukturalny.
<!-- material-card:end -->

## Modelowanie danych w NoSQL



Model zaczyna się od operacji, nie od encji. `AccessPattern` opisuje klucz,
filtry, sortowanie i wymaganą spójność. `AccessPatternDesign` dodaje fizyczną
decyzję: partition key, clustering order, projekcję oraz limit wielkości
partycji.

`evaluate(QueryShape)` nie próbuje automatycznie naprawić nowego zapytania.
Zwraca powody, dla których istniejąca tabela go nie obsługuje:

- brak pełnego partition key jako equality filter oznacza scan lub fan-out,
- kolejność niezgodna z clustering key wymaga sortowania poza partycją,
- brak pola w projekcji oznacza dodatkowy lookup.

Katalog zawiera dwa przykłady: zamówienia użytkownika dla konkretnego statusu
oraz metryki urządzenia w dziennym buckecie. Zapytanie o wszystkie statusy nie
pasuje do pierwszej tabeli. Poprawną decyzją może być druga projekcja
`orders_by_user`, a nie dodanie dowolnego filtra do istniejącej tabeli.

Wartość `expectedMaxItemsPerPartition` jest częścią projektu pojemnościowego.
Nie jest limitem egzekwowanym przez ten model — ma wymusić oszacowanie wzrostu,
retencji i najpopularniejszego klucza jeszcze przed wdrożeniem.
