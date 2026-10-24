# Szablon README projektu LLD

Skopiuj poniższy układ i uzupełnij go rzeczywistym zachowaniem implementacji. Usuń instrukcje redakcyjne. Opis ma być samodzielny: bez odwołań do innych projektów, historii przebudów, nazw wzorców projektowych i porad przenoszenia wiedzy do kolejnych zadań.

Pisz po polsku, konkretnie i w spójnych grupach tematycznych. Łącz powtórzenia, ale zachowuj wiedzę: kontrakty, uzasadnienia, algorytmy, przykłady i granice gwarancji. Nie ustalaj docelowej liczby wierszy. README ma pozwalać zrozumieć rozwiązanie bez czytania wszystkich klas; linki i komentarze kodu uzupełniają wyjaśnienie.

## Nazwa i zakres

Podaj nazwę projektu, jego edukacyjny charakter oraz zaimplementowane funkcje w jednym krótkim akapicie. Ustal model wątkowy i sposób przechowywania stanu. Planowane funkcje oznacz jawnie, jeśli muszą pojawić się w opisie.

## Rdzeń techniczny i mechanizmy Javy

Umieść część techniczną przed rozwinięciem reguł domenowych. Zacznij od operacji,
reprezentacji i niezmienników indeksów. Pokaż działanie na danych i koszty.

Wyjaśnij użyte API JDK: wyszukiwanie klucza lub sąsiada, porządek iteracji,
Comparator i equals/hashCode, kopię kontra widok oraz koszt wzrostu kolekcji.
Przy tablicach i generykach opisz referencje, boxing i alokacje. Jeśli występują
record, sealed, maski bitowe, Supplier lub własne iteratory, pokaż ich konkretną
rolę i pułapkę w tym kodzie. Odwołuj się do kontraktu używanej wersji Javy.

Opisz rzeczywisty dostęp wątkowy: mutowalnego właściciela, efekty odczytów,
publikację widoków i ewentualne leniwe cache. Wyjaśnij konkretny wyścig oraz
dlaczego final, volatile, flaga lub bezpieczna pojedyncza mapa nie rozwiązują
całej operacji. Rozważania o zmianie kontraktu oddziel od istniejącej implementacji;
nie dodawaj niepotrzebnych wątków, harmonogramów ani pul.

### Algorytm i koszty

Wyjaśnij reprezentację danych i powód jej wyboru. Prześledź algorytm krok po kroku na konkretnych danych; opisz niezmiennik, który pozwala uniknąć pełnego skanowania lub kopiowania. Pokaż, jak aktualizacja utrzymuje zgodność indeksów oraz dlaczego prostsze, lecz błędne podejście zawodzi. Dodaj odnośniki do najważniejszych plików kodu. Zdefiniuj symbole i zestaw koszty operacji w tabeli.

Oddziel koszt aktualizacji od raportu i snapshotu. Rozróżnij koszt średni, gwarantowany i amortyzowany. Podaj pamięć, alokacje i koszt zachowywania starszych wersji, jeżeli wpływają na użycie projektu. Diagram dodaj tylko wtedy, gdy upraszcza wyjaśnienie.

## Działanie i kontrakty

Zgrupuj reguły, operacje i dozwolone przejścia. Wyjaśnij identyfikatory, czas, pieniądze i ponowienia, jeżeli występują. Opisz kolejność walidacji oraz odmowy i ich wpływ na stan; wskaż istotne wyjątki od reguły braku zmian. Oddziel błędne dane od naruszenia reguły i awarii zależności.

Reguły biznesowe pozostaw w zakresie potrzebnym do zrozumienia operacji
i testów, bez osobnej rozbudowy scenariuszy organizacyjnych.

## Przepływy i przykłady

Pokaż kompletny przebieg od wejścia przez walidację i obliczenie kandydata do publikacji wyniku. Dodaj odmowę i ważną granicę: dane, wynik oraz stan przed i po. Jeżeli mechanizm wymaga więcej niż jednego przykładu, opisz je razem. Nie zastępuj rozumowania samą listą komend.

## Uruchomienie

Podaj wymagany JDK, katalog, komendę wrappera i demo oraz najważniejsze argumenty. Pełną składnię poleceń można pozostawić w help konsoli. Nie deklaruj wykonanego buildu na podstawie samego istnienia komendy.

## Testy i ograniczenia

Powiąż istotne gwarancje z konkretnymi testami. Wyjaśnij, jak niezależna wyrocznia lub model porównawczy wykrywa błąd, jaki zakres danych pokrywa test i czego nie dowodzi. Odróżnij sprawdzenie wyniku od kontroli wysokości drzewa, liczby rekordów indeksu lub innego dowodu kosztu. Zapisz ograniczenia wpływające na użycie. Nie deklaruj wykonanej weryfikacji na podstawie samego opisu testu.
