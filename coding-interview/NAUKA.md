# Poziomy, wskazówki i samodzielność

[Mapa](README.md) · [Bank ćwiczeń](exercises/README.md) · [Trening mieszany](practice/README.md)

## Jak czytać poziom

L1 oznacza jeden główny mechanizm i jawny stan, L2 łączy mechanizm z dodatkowymi
warunkami (duplikaty, odtwarzanie, wiele granic), a L3 wymaga trudniejszego
niezmiennika, dodatkowego wymiaru stanu albo wyraźnego odcinania kandydatów.
To podział tego modułu, niezależny od etykiet i limitów dowolnej platformy.
L1 nadal wymaga spełnienia całego kontraktu, w tym głębokich wejść i braku mutacji.

Przed zadaniem przeczytaj jego wymagania. Nie otwieraj wszystkich kart naraz.
Kolejność zależności w [planie](PLAN.md) zaczyna się od operacji na strukturach,
potem przechodzi do wzorców i łączenia technik. Karta o ewolucji DP pokazuje na
jednym problemie rekurencję, memoizację, tabulację i ograniczenie pamięci.

## Protokół wskazówek

1. Zapisz własny kontrakt, brute force i jedną hipotezę optymalizacji.
2. Jeśli utkniesz, otwórz tylko wskazówkę 1: obserwację o danych lub wyniku.
3. Wskazówka 2 sugeruje stan albo strukturę. Po niej ponownie sam wyprowadź krok.
4. Wskazówka 3 podaje kierunek algorytmu. Napisanie kodu i dowodu nadal należy do ciebie.

Próba ze wskazówką jest pełnowartościową nauką, ale odnotuj jej użycie. Nie oznaczaj
jej jako samodzielnego rozpoznania techniki. Nie ma obowiązku czekania określonej
liczby minut; sięgnij po wskazówkę po zapisaniu, co już rozumiesz i gdzie jest luka.

## Co zapisać po zadaniu

| Pole | Przykładowa treść notatki |
| --- | --- |
| Samodzielność | bez pomocy / wskazówka 1, 2 lub 3 / po lekturze karty |
| Model | spójny zakres, półotwarte granice, powtórzenia dozwolone |
| Błąd | użyłem tego samego indeksu dwa razy |
| Dowód | mapa zawiera wyłącznie wcześniejsze indeksy |
| Wynik | testy przeszły; ręcznie uzasadniony czas i pamięć |
| Powtórka | po kilku dniach bez podglądania, następnie wariant ze zmienionym warunkiem |

Zielony test i brak pomocy nie są tym samym. Oceniaj osobno rozpoznanie techniki,
poprawność kodu, wyjaśnienie niezmiennika oraz analizę kosztu. Zestawy mieszane
przenoszą ciężar na pierwszą z tych umiejętności.
