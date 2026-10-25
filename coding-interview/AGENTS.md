# Zasady modułu coding-interview

- To materiał edukacyjny po polsku. Czytaj README.md i METODYKA.md przed zmianą.
- Kod algorytmów i scenariuszy umieszczaj tylko w Markdown. Nie dodawaj osobnych
  źródeł Java, artefaktów kompilacji ani buildu aplikacji.
- Karta ma kontrakt, brute force, obserwację i niezmiennik, przykład krok po kroku,
  fragment Javy, złożoność, kontrprzykład, scenariusze i ćwiczenie.
- Ujawniaj założenia: sortowanie, alfabet, znaki liczb, duplikaty, mutację wejścia,
  brak wyniku i zakres wartości. Nie przypisuj własnego kontraktu platformie.
- Koszt obejmuje struktury pomocnicze, stos rekurencji i rozmiar wyniku.
  Rozróżniaj koszt oczekiwany, amortyzowany i najgorszy.
- Bloki java w patterns/ mają składowe klasy bez importów, pakietu i zewnętrznej
  klasy. Każda karta definiuje static void verify(); asercje muszą być niezależne
  od przypadkowej kolejności HashMap i PriorityQueue.
- Nie wprowadzaj bloków java z celowo błędnym kodem do kart: checker kompiluje
  wszystkie takie bloki. Kontrprzykłady opisuj słownie lub oznacz jako text.
- Utrzymuj zgodność katalogu README z opracowanymi kartami. Tematy planowane
  oznaczaj jawnie; nie twórz pustych kart udających ukończony materiał.
- Bank exercises/ to osobny materiał: obecnie trzy nierozwiązane zadania na kategorię.
  Zachowuj oznaczenia exercise:ID i tests:ID, kontrakt oraz testy. Nie dopisuj
  rozwiązań bez zlecenia użytkownika. Typy wejścia i helpery testów nie są rozwiązaniem.
- check-exercises.ps1 domyślnie tylko kompiluje szkielety i testy, raportując TODO.
  -Exercise ID lub -RunAll uruchamia verify() z asercjami, a -RunSolved wykonuje
  metody bez znacznika TODO. Nie przedstawiaj samej
  kompilacji jako zaliczonych testów nierozwiązanych zadań.
- Weryfikacja z modułu: pwsh -File scripts/check-examples.ps1 (JDK 21+).
  Dla banku zadań także pwsh -File scripts/check-exercises.ps1.
  Zmiany parsera lub uruchamiania sprawdzaj scripts/test-runner.ps1.
  Zadania mają poziom, wymagania i trzy rozwijane wskazówki. Nie koduj w skryptach
  stałej liczby kategorii ani zadań. Zestawy practice/ mają neutralne ID i kopie
  robocze metod; źródło kategorii jest ukryte. Nie nadpisuj rozwiązywanego zestawu.
  Z korzenia: pwsh -File scripts/check-documentation.ps1 i git diff --check.
  Skrypt może generować klasy wyłącznie w swoim katalogu tymczasowym.
- verification/ zawiera jawnie oddzielone rozwiązania referencyjne i mutacje
  do walidacji testów; nie kopiuj ich automatycznie do banku lub prób użytkownika.
  Uruchamiaj scripts/check-test-quality.ps1 po zmianie testów lub kontraktów.
  Sam mechanizm oceny referencji i mutacji sprawdzaj scripts/test-quality.ps1.
- debugging/ ma celowo błędny kod, który powinien kompilować się i początkowo
  nie przechodzić testów. Nie traktuj go jak gotowych przykładów patterns/.
- Zachowuj pochodzenie i snapshot zestawów mieszanych. Kontrola zgodności jest
  tylko do odczytu; nie zmieniaj skrótów w celu ukrycia rozjazdu. Samotesty
  filtrów i ochrony pracy są w scripts/test-practice.ps1.
- Diagnostyka Javy powinna odsyłać do rzeczywistych linii Markdown. Otoczenie
  generowane oznaczaj jawnie. Sprawdzaj mapowanie samotestami skryptów.
