# Java: struktury, złożoność i pułapki

[Mapa modułu](README.md) · [Metodyka](METODYKA.md)

Przykłady odnoszą się do Java 21. Używamy modelu operacji na liczbach o stałym
rozmiarze; przejście na BigInteger wymaga dodatkowej analizy kosztu arytmetyki.
`null` jest poza kontraktem kart, chyba że oznacza pusty korzeń lub koniec listy.

## Struktura wynika z operacji

| Struktura | Typowe operacje i koszt | Pułapka |
| --- | --- | --- |
| `int[]`, `long[]` | indeks O(1), przegląd O(n) | stały rozmiar i granice indeksów |
| `ArrayList` | indeks O(1), dopisanie amortyzowane O(1) | usunięcie środka przesuwa elementy: O(n) |
| `HashMap`, `HashSet` | podstawowe operacje oczekiwane O(1) przy dobrym hashowaniu | nie obiecuj najgorszego O(1) ani kolejności iteracji |
| `TreeMap`, `TreeSet` | wyszukiwanie i modyfikacja O(log n) | porządek komparatora określa też równoważność kluczy |
| `ArrayDeque` | stos lub kolejka; końce amortyzowane O(1) | brak `null`; wyszukiwanie elementu O(n) |
| `PriorityQueue` | `offer`, `poll` O(log n), `peek` O(1) | iterator nie jest posortowany; `contains` i usunięcie obiektu O(n) |

Dla kolejki BFS stosuj `addLast` i `removeFirst`, dla stosu `push` i `pop`.
Tablica liczników ma sens przy małym, znanym alfabecie; mapa przy rozproszonej
dziedzinie kluczy. Sam wybór kolekcji nie zmienia wymaganej liczby operacji.
Koszty i kontrakty sprawdzisz w [dokumentacji API](MATERIALY.md).

## Liczby i porównania

- Suma dwóch `int` może się przepełnić. Konwersja musi nastąpić przed dodawaniem:
  `(long) a + b`. Zapis `long sum = a + b` wykonuje najpierw dodawanie `int`.
- Komparator `a - b` może odwrócić znak po przepełnieniu. Stosuj
  `Integer.compare(a, b)` lub `Comparator.comparingInt(...)`.
- Środek przedziału licz jako `left + (right - left) / 2` dla nieujemnych indeksów.
- `Integer` porównuj przez `equals` lub po rozpakowaniu. `==` między obiektami
  sprawdza referencje; małe wartości mogą ukryć błąd dzięki cache opakowań.
- Liczba rozwiązań może wymagać `long`, nawet jeśli pojedyncze wartości są `int`.
  Liczba spójnych niepustych fragmentów tablicy to n(n + 1)/2.

## Referencje, mutacja i tekst

`Arrays.sort(a)` zmienia tablicę wejściową. `Arrays.copyOf` kopiuje tablicę
referencji płytko; dla `int[][]` zewnętrzna kopia nie kopiuje wierszy. Karta
przedziałów tworzy nowe wiersze, aby wynik nie współdzielił stanu z wejściem.

Backtracking musi kopiować aktualną ścieżkę przy zapisie wyniku. Dodanie tej
samej listy wiele razy zapisuje wiele referencji do jednego zmieniającego się
obiektu. `List.of` tworzy listę niemodyfikowalną — nie nadaje się jako bufor ścieżki.

`char` i `String.length()` dotyczą jednostek UTF-16, nie wszystkich znaków
Unicode. Karta sliding window celowo zakłada ASCII. Dla pełnego Unicode najpierw
ustal, czy operujesz na code pointach, czy grafemach, oraz jak liczysz indeksy.

## Czytanie złożoności

O(n) opisuje skalowanie, nie liczbę milisekund. Pamięć pomocniczą oddzielaj od
pamięci wejścia i wyniku. Dla DFS dopisz O(h) stosu; w drzewie zdegenerowanym
h = n, więc rekurencja może skończyć się StackOverflowError. Wypisanie wszystkich
podzbiorów wymaga Θ(n·2ⁿ) miejsca na skopiowane elementy; same 2ⁿ decyzji końcowych
nie obejmują kosztu materializacji wyniku.

Amortyzowane O(1) opisuje koszt serii operacji, oczekiwane O(1) zależy od założeń
o hashowaniu. Nie są to gwarancje stałego czasu każdej pojedynczej operacji.
