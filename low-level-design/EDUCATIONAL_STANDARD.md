# Standard edukacyjny projektów Low Level Design

Aktualna ścieżka ma **12 głównych tematów i osobną rozgrzewkę**. Bankomat
jest wariantem automatu, a auta i sale należą do jednego kalendarza zasobów.
Historię edytora rozwijamy wokół rope i undo/redo; workflow ról pozostaje
archiwalnym materiałem opcjonalnym. Nowy główny temat musi wnosić odrębny
problem techniczny; najpierw rozważ wariant w istniejącym module.

Wszystkie ćwiczenia w [ścieżce LLD](README.md) są **projektami edukacyjnymi**.
Ich rezultatem jest model, który można uruchomić, zrozumieć i samodzielnie
zmienić, oraz materiał pozwalający odtworzyć rozumowanie po dłuższej przerwie.
Dokumentacja, komentarze i testy są częściami tego samego wyjaśnienia.

## Co ma wynikać z lektury

Głównym celem jest techniczne rozwiązanie problemu: operacje → struktura danych
→ algorytm → koszt → dowód poprawności. Czytelnik powinien umieć uzasadnić,
dlaczego wybrano kopiec, kolejkę, indeks drzewa, tablicę lub listę wiązaną,
i pokazać, czego unika się względem skanowania oraz sortowania całej kolekcji.
Komentarze mają wyjaśniać także alokacje, boxing i lokalność danych.
Modele biznesowe pozostają możliwie małe; dodatkowy typ musi chronić kontrakt
albo usprawniać implementację, a nie tylko dodawać terminologię.

README każdego projektu zawiera sekcję „Rdzeń techniczny”: operacja, struktura,
złożoność i kompromis. Koszt snapshotów, raportów i utrzymania indeksów podajemy
osobno. Nie uznajemy zmiany dokumentacji lub zamiany nazw klas za przebudowę kodu.

Czytelnik powinien umieć wskazać właściciela każdej reguły, wyjaśnić kontrakt
operacji, przewidzieć zachowanie przy błędzie, porównać alternatywy i sprawdzić
swoje rozumienie przez test. README opisuje konkretne działanie i wyjaśnia
rozumowanie techniczne; komentarze pokazują jego realizację w kodzie.

Edukacyjny charakter uzasadnia bogatsze komentarze, małą domenę i ograniczoną
infrastrukturę. Nie uzasadnia błędnej logiki, fikcyjnych wyników testów ani
deklarowania gwarancji, których implementacja nie zapewnia.

## Mechanizmy Javy jako główny przedmiot wyjaśnienia

README zaczyna się krótkim zakresem, następnie rdzeniem technicznym i opisem
użytych mechanizmów Javy. Reguły domenowe uzasadniają operacje i testy;
nie powinny wypierać wyjaśnienia kolekcji, pamięci i modelu wątkowego.

| Obszar | Oczekiwane wyjaśnienie w kontekście kodu |
| --- | --- |
| Kolekcje | Co wykonują get/put/remove, wyszukanie sąsiada i iterator; koszt wzrostu, sortowania i materializacji |
| Równość i porządek | Jak działają klucze, equals/hashCode i wynik Comparator równy zero; skąd bierze się kolejność wyniku |
| Pamięć | Tablica prymitywna kontra referencje i boxing; płytka kopia, współdzielenie, leniwy cache oraz osiągalność przez GC |
| Typy i język | Znaczenie record, sealed, generyków, CharSequence, operatorów bitowych i dokładnej arytmetyki, jeśli są użyte |
| Wątki | Mutowalny właściciel kontra niezmienny widok; reentrancja, widoczność, atomowość i wspólna granica aktualizacji indeksów |

Nie twórz encyklopedii wszystkich klas JDK. Każde wyjaśnienie ma dotyczyć
mechanizmu występującego w implementacji i pokazywać jego konkretną konsekwencję.
Podawaj odnośnik do właściwego API lub JLS dla nieoczywistego kontraktu.
Nie przedstawiaj szczegółów implementacji jako gwarancji każdej JVM.

Jednowątkowość też wymaga uzasadnienia. Wyjaśnij, które operacje mogłyby
utracić aktualizację lub rozjechać indeksy i dlaczego sama kolekcja współbieżna
nie rozwiązuje całej operacji. Wskazuj stan faktyczny: brak synchronizacji
nie może być opisany jako gotowy wariant wielowątkowy. Nie dodawaj fikcyjnego
ExecutorService ani Thread.sleep do deterministycznego modelu.

## Komentarze w kodzie

Przejrzyj wszystkie własne klasy źródłowe i testowe. Komentarze po polsku
powinny uzupełniać kod w miejscach wymagających rozumowania:

| Miejsce | Co wyjaśnić |
| --- | --- |
| Klasa lub typ domenowy | Odpowiedzialność, modelowane pojęcie i granica tej odpowiedzialności |
| Konstrukcja poprawnego stanu | Dlaczego walidacja jest tutaj i jakiego niespójnego stanu zabrania |
| Operacja zmieniająca model | Warunki wejściowe, efekt, zachowanie przy odmowie i kolejność kroków |
| Algorytm | Założenia poprawności, przypadek brzegowy, złożoność i prostsza alternatywa |
| Widoczność i mutowalność | Kto może tworzyć lub zmieniać obiekt, co jest współdzielone i dlaczego bezpiecznie |
| Adapter wejścia/wyjścia | Co należy do prezentacji, co do domeny, kto posiada zasoby i jak obsługiwane są błędy |
| Test i jego dane | Jaką gwarancję sprawdza przykład, jaki błąd wykrywa i czego nie dowodzi |

Nie dopisuj opisu do każdego gettera i każdej instrukcji. Użyteczny komentarz
odpowiada na „dlaczego?” lub „co by się zepsuło?”. Np. przy kopiowaniu planszy
wyjaśnij konieczność skopiowania zmienianego wiersza i wskaż test starszych
widoków, zamiast jedynie napisać „klonujemy tablicę”.

Objaśniaj nowe terminy przez konkretny fragment. `final` chroni referencję przed
ponownym przypisaniem, lecz nie zawartość tablicy. Snapshot to widok w danej
chwili, a nie automatycznie historia ani serializacja. Wzorzec projektowy
powinien odpowiadać na występującą zmienność. Wskazuj też koszt i sytuację,
w której obecne uproszczenie przestaje wystarczać.

## Konkretny, samodzielny README

Stosuj [szablon README](templates/PROJECT_README.md). Opis dotyczy wyłącznie
danego projektu, bez porównań do innych zadań, nazw wzorców projektowych,
historii przebudowy i wniosków do kolejnych ćwiczeń.

Zachowaj sześć grup informacji:

1. edukacyjny charakter, zaimplementowany zakres i model wykonania;
2. działanie, najważniejsze kontrakty, niezmienniki i efekt odmów;
3. struktury, algorytm, koszty aktualizacji, raportów, snapshotów i pamięci;
4. prześledzone przykłady sukcesu, odmowy i istotnych przypadków brzegowych;
5. dokładne komendy uruchomienia oraz argumenty;
6. zakres testów, model porównawczy i ograniczenia.

Skracaj powtórzenia, a nie wyjaśnienia. Nie stosuj limitu długości, który usuwa
uzasadnienie wyboru struktury, przebieg algorytmu lub przykład. Po lekturze
czytelnik ma móc wyjaśnić, jak i dlaczego działa rozwiązanie, co zmienia się
w indeksach oraz co pozostaje po błędzie, bez konieczności otwierania źródeł.

Grupuj reguły i decyzje, zamiast rozbijać tekst na osobne hasła, cele, słowniki
i dziesiątki pytań. Istotne odpowiedzi włączaj do opisu odpowiedniego mechanizmu.
Tabela ma ułatwiać porównanie operacji lub wskazywać dowody w testach; sam wykaz
nazw klas nie zastępuje wyjaśnienia. Diagram jest pomocny, gdy pokazuje stan,
przepływ lub współdzielenie danych. Szczegóły składni i lokalne decyzje pozostają
w komentarzach kodu, a rozumowanie techniczne jest obecne również w README.

Linkuj do istniejących źródeł i testów. Indeks obszaru służy nawigacji między
projektami; opisy wariantów jednego modułu mogą korzystać z lokalnego indeksu.
Przekierowania w dawnych katalogach pozostają krótkimi odnośnikami.

## Testy i eksperymenty

Test zachowania pokazuje dowód w granicach swoich danych. Test wyczerpujący
skończony zbiór przypadków i test generujący próbkę mają różną siłę wniosku.
Wyjaśniaj tę różnicę. W testach generowanych ustal ziarno losowania, a wyrocznię
zaprojektuj na tyle odmiennie, aby nie skopiować badanego błędu.

Eksperyment to mała, lokalna zmiana z przewidywanym skutkiem. Najpierw przewidź
wynik, potem uruchom wskazany test i wyjaśnij różnicę. Celowy kontrprzykład
wykonuj na kopii lub odwracalnie; przed zakończeniem przywróć poprawny kod.
Nie dodawaj błędnych wariantów do podstawowego API tylko na potrzeby opisu.

## Przegląd przed zakończeniem

Sprawdź zgodność komentarzy z kodem, przykładów z kontraktami i opisu testów
z rzeczywistą weryfikacją. Usuń nieaktualne obietnice, odwołania wymagające znajomości rozmowy
i komentarze powtarzające składnię. Czytelnik ma wiedzieć, co działa, co jest
założeniem i co pozostaje propozycją rozszerzenia.

Weryfikację techniczną wykonuj według [procedury](PROCEDURE.md). Materiał można
uznać za gotowy do nauki, gdy czytelnik potrafi prześledzić operację, wskazać
chroniony niezmiennik, uruchomić dowód i wyjaśnić koszt alternatywy.
