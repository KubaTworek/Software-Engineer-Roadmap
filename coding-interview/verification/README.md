# Weryfikacja testów — obszar zawierający rozwiązania

[Mapa modułu](../README.md) · [Opis testów](../TESTOWANIE.md)

Ten katalog jest przeznaczony do utrzymania materiału **po własnej próbie**.
Zawiera implementacje referencyjne i celowo błędne mutacje w Markdown. Bank
exercises/ i treningi practice/ zachowują własne, nierozwiązane szkielety.
Skrypt nie kopiuje rozwiązań do tych obszarów.

Kontrola wymaga, aby oryginalny verify() każdego zadania przeszedł dla referencji.
Następnie wskazane, kompilujące się mutacje mają zostać odrzucone przez asercje.
Błąd kompilacji mutanta nie jest uznawany za wykrycie błędu przez testy.
Mutant ocalały jest luką w tym konkretnym sprawdzeniu i powoduje błąd kontroli.

Z katalogu coding-interview, PowerShell 7 i JDK 21+:

```powershell
pwsh -File scripts/check-test-quality.ps1
```

Można podać -JdkHome i zawęzić kontrolę parametrem -Exercise. Raport oddziela
liczbę poprawnych referencji, wykrytych mutacji, ocalałych i błędów narzędziowych.
To próbka typowych pomyłek, nie pełna automatyczna analiza wszystkich mutacji.
Przejście referencji nie jest matematycznym dowodem poprawności oczekiwań;
dlatego zachowujemy też opis kontraktów, oracle i ręcznie wyprowadzone przykłady.

Typy wejścia są skopiowane z szkieletów, aby referencje miały ten sam kontrakt.
Warianty TREE-02–03 świadomie zużywają O(n) pamięci zamiast stosu rekurencji.
Niektóre inne funkcje wybierają prostotę nad najmniejszy koszt stały; muszą
obsługiwać rozmiary i wymagania właściwego zadania.
