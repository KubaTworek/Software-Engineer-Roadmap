# Co sprawdzają testy

[Mapa](README.md) · [Bank](exercises/README.md)

## Cztery różne wyniki kontroli

PASS oznacza, że wykonano verify() z włączonymi asercjami i bez błędu. FAIL oznacza
błąd kompilacji albo testów. TODO oznacza niedokończony szkielet, a COMPILED metodę
bez TODO, która została tylko skompilowana. Domyślna kontrola banku nie wykonuje
testów; -RunSolved wykonuje metody bez TODO. -RunAll próbuje wszystkich i zbiera
raport, nawet po błędzie poszczególnych zadań. Pozostałe TODO powodują wtedy błąd
końcowy. Błąd konfiguracji JDK albo uszkodzona struktura dokumentu jest błędem
całej kontroli, a nie przypadkiem testowym danego algorytmu.

## Przypadki przykładowe, właściwości i oracle

Testy jawne sprawdzają wynik na konkretnych danych. Testy własności sprawdzają
np. każdą krawędź zwróconej ścieżki albo unikalność permutacji. W wybranych
zadaniach mały brute force jest oracle: prostym niezależnym sposobem ustalenia
oczekiwania. Kod oracle jest częścią testów, nie uzupełnieniem metody TODO.

| Zadanie | Niezależne sprawdzenie | Ziarno |
| --- | --- | ---: |
| TWO-03 | wszystkie pary indeksów | 203 |
| WIN-01 | każda suma okna liczona od nowa | 301 |
| WIN-02 | wszystkie początki i końce oraz zbiór znaków | 302 |
| PRE-02 | bezpośrednie liczenie zer i jedynek fragmentu | 402 |
| STACK-03 | wszystkie zakresy i ich minimalna wysokość | 703 |
| DP-01 | wszystkie podzbiory z odrzuceniem sąsiednich indeksów | 1401 |
| GREEDY-01 | wszystkie podzbiory i pary kolidujących spotkań | 1501 |

Stałe ziarna dają powtarzalne wejścia w obrębie użytej implementacji Random.
Asercje podają numer próby. Po błędzie zapisz konkretne dane i minimalizuj je,
zachowując błąd: skróć tablicę, zmniejsz wartości, usuń zbędne krawędzie. Dopisz
uzyskany kontrprzykład jako osobną asercję, aby test nie zależał tylko od generatora.

## Większe wejścia

TREE-01–03 oraz BST-01 sprawdzają łańcuch 10 000 węzłów, BFS-01 i BFS-03 łańcuch
100 000 wierzchołków, LIST-01 listę 100 000 węzłów. Testy z dużą licznością par
i sumami poza int rozróżniają typ wyniku od typu elementu. Gotowe karty również
mają wybrane testy większych wejść oraz różnicowe porównania.

To nie benchmarki. Duże dane pomagają ujawnić przepełnienie stosu i niektóre
błędy skali, ale nie dowodzą Big O. Nie dodajemy asercji „musi trwać mniej niż
X milisekund”, zależnych od obciążenia maszyny. Każdy proces JDK ma techniczny
timeout 45 sekund chroniący uruchamianie przed zawieszeniem.

## Samotesty narzędzi i CI

scripts/test-runner.ps1 tworzy wyłącznie tymczasowe, sztuczne kontrakty. Sprawdza
PASS, złą odpowiedź, TODO, błąd kompilacji, wybór jednego zadania, nieznane i
powtórzone ID oraz generator treningów. Te kontrakty nie są rozwiązaniami banku.
Uruchom go po zmianie parsera lub raportowania. CI wykonuje także kontrole kart,
banku, czterech mieszanych zestawów i odsyłaczy.

## Weryfikacja oczekiwań i mutacje

scripts/check-test-quality.ps1 wykonuje niezmieniony verify() każdego zadania
na osobnej implementacji referencyjnej. Obecnie pokrywa 72 kontrakty banku
i 8 zadań diagnostycznych. Następnie sprawdza 32 celowo błędne, kompilujące się
warianty: po jednym dla wybranej funkcji z każdej kategorii oraz osiem błędów
diagnostycznych. Każdy ma zostać wykryty przez AssertionError. Błąd kompilacji,
inny wyjątek albo ocalały mutant powoduje błąd całej kontroli.

Pełne rozwiązania są w [verification/](verification/README.md), oddzielnie od
banku. Nie są automatycznie wklejane do TODO ani zaliczane jako praca uczącego się.
Wynik REFERENCES=80/80 KILLED=32 SURVIVED=0 ERRORS=0 oznacza zaliczenie tej
konkretnej kontroli, nie dowód poprawności dla wszystkich możliwych wejść.
Zbiór mutacji jest jawnie wybraną próbką, nie pełną analizą pokrycia mutacyjnego.
Samotest test-quality.ps1 potwierdza, że ocalały mutant, błąd jego kompilacji
i niezaliczona referencja rzeczywiście psują kontrolę; nie wystarczy sam czerwony
proces Javy, aby uznać mutację za wykrytą.

Kontrola ujawniła i pozwoliła poprawić oczekiwanie PRE-03 dla 100 000 zer:
liczba niepustych fragmentów to n(n+1)/2 = 5 000 050 000. n(n-1)/2 liczy pary
różnych indeksów i ma inne znaczenie. Tę różnicę sprawdzają też małe przypadki.

## Lokalizacje błędów i kopie treningów

Każdy blok ma mapę między liniami tymczasowej klasy Java i źródłowego Markdown.
Kompilacja wskazuje linię implementacji, a stos AssertionError linię verify().
Otoczenie generowane ma jawne oznaczenie zamiast udawanej lokalizacji rozwiązania.
Samotesty narzędzia sprawdzają oba rodzaje mapowania. Końce linii CRLF są
normalizowane bez zmiany numerów linii.

scripts/test-practice.ps1 używa małego tymczasowego banku do sprawdzenia filtrów,
wykluczeń, wykrywania zmian źródła i kopii oraz ochrony rozwiązania przy -Force.
scripts/check-practice.ps1 kontroluje pochodzenie zapisanych zestawów bez ich
nadpisywania. CI uruchamia obie kontrole, walidację referencji i kompilację
[zadań diagnostycznych](debugging/README.md), których początkowy FAIL jest celowy.
