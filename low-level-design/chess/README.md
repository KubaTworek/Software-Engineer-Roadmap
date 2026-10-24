# Szachy

Projekt edukacyjny w czystej Javie: reprezentacja pozycji, generowanie legalnych ruchów i cykl partii. Implementacja zawiera ruchy specjalne, reguły remisów, zapis FEN, cofanie poleceń i zliczanie drzewa ruchów. Stan jest w pamięci, obsługa jednowątkowa.

Materiały do nauki: [lektury i źródła tego ćwiczenia](../MATERIALY.md#szachy).

## Rdzeń techniczny

[Board](src/main/java/pl/jakubtworek/lld/chess/domain/Board.java) łączy Piece[64] z maskami long dla kolorów i rodzajów figur. Tablica służy odczytowi pól, maski szybkiemu odnajdywaniu figur i króla. Zmieniona plansza kopiuje 64 pola; mapowy widok pól powstaje leniwie.

[ChessGame](src/main/java/pl/jakubtworek/lld/chess/domain/ChessGame.java) utrzymuje liczności kluczy pozycji. Klucz zawiera figury, turę i prawa roszady; en passant tylko wtedy, gdy istnieje legalne takie bicie. Liczniki ruchów nie należą do klucza. Undo usuwa przyrost licznika odpowiedniej pozycji.

| Operacja | Koszt i granica |
| --- | --- |
| Sprawdzenie ataków | O(8L + 8), L ≤ 7 pól na promieniu |
| Walidacja kandydatów ruchu | O(C × (64 + L)), C oznacza liczbę kandydatów |
| Dopisanie historii | O(1), niemutowalna lista wiązana |
| Undo | Średnio O(1), wraz z korektą mapy powtórzeń |
| Odczyt historii chronologicznie | O(H) czasu i pomocniczej pamięci; odczyt po indeksie O(H) |
| Zachowanie pozycji historii | O(64H + D), D to liczba różnych kluczy pozycji |

Perft zlicza legalne ścieżki do zadanej głębokości, pomijając zakończenia wynikające z historii remisów. Głębokość zero daje jeden węzeł; divide rozbija wynik na pierwsze ruchy. Koszt rośnie wykładniczo z głębokością. Arytmetyka licznika wykrywa przepełnienie long. Perft korzysta z tego samego generatora legalności, więc sam nie stanowi niezależnej wyroczni.

### Legalność ruchu a tożsamość pozycji

Tablica 64 pól i maski bitowe opisują tę samą planszę. Każde utworzenie
zmienionej planszy musi zachować ich zgodność. Maska pozwala znaleźć króla,
a promienie i przesunięcia figur wyznaczają ataki bez tworzenia mapy wszystkich
pól na potrzeby każdego sprawdzenia.

Ruch geometrycznie poprawny jest dopiero kandydatem. Silnik tworzy jego
wynik, uwzględnia zbicia i ruchy specjalne, a następnie bada bezpieczeństwo
własnego króla. Przy en passant usunięcie pionów może otworzyć linię wieży;
przy roszadzie potrzebne są również bezpieczne pola przejścia.

Klucz powtórzenia nie jest samym tekstem FEN. Pomija liczniki, lecz zachowuje
prawa roszady. Cel en passant odróżnia pozycje tylko przy rzeczywiście legalnym
biciu. Nielegalne bicie związanym pionem nie tworzy dodatkowego dostępnego ruchu.

Historia ma odnośnik do poprzedniego stanu i informację, który klucz został
dodany do licznika. Undo przywraca korzeń i odejmuje ten przyrost. Sklonowanie
całej mapy powtórzeń po każdym półruchu dawałoby zbędny narastający koszt;
lokalna delta pozwala zachować cofanie bez takiego kopiowania.

## Mechanizmy Javy i model wątkowy

### Tablice, long i leniwy widok mapowy

Piece[64] zawiera referencje do figur. cells.clone() kopiuje tablicę
referencji, nie każdy obiekt Piece; to bezpieczne, ponieważ figury są
niemutowalnymi wartościami. long[][] masek jest tablicą osobnych wierszy.
Tablice nie są wystawiane do modyfikacji przez publiczne API.

Bit pola tworzymy przez 1L << index. L jest istotne: 1 << index operuje
na int. Java maskuje dystans przesunięcia long do sześciu bitów,
więc 1L << 64 jest tym samym co 1L << 0. Indeksy muszą mieścić się w 0–63.
Ustawiony bit 63 daje ujemny long, ale nadal jest poprawną maską;
test niepustości to mask != 0, a nie mask > 0.

Mapa pieces() jest materializowana leniwie. Klient wygodnego API płaci
za utworzenie wpisów mapy i Square, natomiast wewnętrzne obliczenia
korzystają z tablic i masek. Prywatne, nie-finalne pole view jest cache
odczytu, mimo że logiczna plansza nigdy nie zmienia figur.

HashMap liczników pozycji używa wartościowych kluczy. Hash nie wystarcza
do uznania dwóch pozycji za równe. Odwracalna zmiana licznika dotyczy
jednego wpisu; historia zachowuje poprzedni stan bez kopiowania całej mapy.

### Niemutowalna pozycja a właściciel partii

ChessGame zmienia wskaźnik historii oraz mapę wystąpień. Równoległe play
lub undo mogłyby je rozjechać, nawet przy współdzieleniu niezmiennych plansz.
Leniwe view w Board również nie jest opisane jako synchronizowany cache.
Nie należy wnioskować o pełnym bezpieczeństwie wątkowym całego API z
samego final na polach tablic lub niemutowalności figur.

Analiza perft używa tych samych reguł legalności. Nie wykonuje parallelStream
ani zadań w puli; wyniki testów nie są dowodem poprawności równoległej gry.
Semantyka przesunięć: [JLS 15.19](https://docs.oracle.com/javase/specs/jls/se21/html/jls-15.html#jls-15.19).

## Zakres, reguły i niezmienniki

Dwie lokalne strony grają na planszy 8 × 8; w nowej partii zaczynają białe.
Jedna instancja jest obsługiwana w **jednym wątku**, bez sieci, zegarów i pieniędzy.
Kolor komendy nie jest uwierzytelnieniem człowieka. Dane pozostają w pamięci.

| ID | Wymaganie i kryterium akceptacji |
| --- | --- |
| R1 | Geometria, bicia, blokady: e2-e4 legalne, e2-e5 odrzucone; skoczek przeskakuje figury |
| R2 | Król nie może pozostać pod atakiem; mat/pat kończą grę, króla się nie zbija |
| R3 | Odrzucony ruch lub komenda nie zmienia snapshotu, tury, historii ani uprawnień |
| R4 | Odczyty, historia i mapy są niemutowalne; stare snapshoty zachowują wartości |
| R5 | Roszada, cztery promocje, en passant z oknem jednej odpowiedzi |
| R6 | `evaluate` zwraca sukces/odmowę; generator ogranicza kandydatów, `hasLegalMove` kończy ocenę po pierwszym sukcesie |
| R7 | FEN przenosi sześć pól; błędny import nie zastępuje gry |
| R8 | Perft/divide oraz testy pozycji referencyjnych, także z ruchami specjalnymi |
| R9 | Trzecie wystąpienie lub 100 półruchów daje prawo zgłoszenia; piąte lub 150 kończy automatycznie; mat ma pierwszeństwo przed 150 |
| R10 | Wynik ma przyczynę i opcjonalnego zwycięzcę; rezygnacja jest osobną komendą |
| R11 | Undo przywraca całą poprzednią wersję, a nowa gałąź nie dziedziczy porzuconych powtórzeń |

Poza zakresem: AI, Chess960, SAN/PGN, zegary, trwałość, redo, oferty remisu,
pełne rozpoznawanie martwych pozycji i procedury/konsekwencje turniejowe.
Rezygnacja w tym modelu oznacza przegraną rezygnującego również wtedy, gdy
przeciwnik nie mógłby zamatować. Nie implementujemy tu wyjątku regulaminowego
zależnego od niemożności mata. Niepoprawne zgłoszenie jest zwykłą odmową:
bez kar czasowych, obowiązku wykonania zapowiedzianego ruchu i procedury arbitra.

- Na polu jest najwyżej jedna figura; własnej figury nie wolno bić. Oba króle
  pozostają na planszy. Zbicie króla daje `KING_CAPTURE`, nie wygraną.
- Ochronę króla badamy po wszystkich efektach: przy en passant znika pion poza
  celem, a przy roszadzie zmieniają pozycję król i wieża. Atakowany jest również
  pusty skos piona; figura związana nadal atakuje.
- Prawo roszady znika po ruchu króla/wieży i biciu wieży na jej narożniku.
  Powrót figury go nie odtwarza. Okno en passant wygasa po zaakceptowanej
  odpowiedzi, nie po błędnej próbie. Promocja jest jednym ruchem, bez stanu
  pośredniego z pionem na ostatniej linii.
- Licznik półruchów zeruje ruch piona lub bicie; pełny numer rośnie po czarnych.
  Liczniki nie należą do tożsamości powtórzenia. Cel en passant należy do niej
  tylko przy istnieniu legalnego bicia, także po sprawdzeniu własnego króla.
- Wynik remisu nie ma zwycięzcy; mat/rezygnacja mają. Zakończona gra nie
  udostępnia ruchów ani zgłoszeń. Cofnięcie zakończenia może ją ponownie otworzyć.
- Nieudana operacja nie tworzy wersji undo. Wersja przechowuje również mapę
  powtórzeń; przywrócenie samej planszy prowadziłoby do fałszywych remisów.

`Position.analysis` zachowuje rolę lekkiego narzędzia do testów: dokładnie dwa
króle, brak pionów na skrajnych liniach, bez historycznych praw. Może przyjąć
nieosiągalny układ, np. króla strony niemającej ruchu już szachowanego.
`FenCodec.parse` dodaje walidację lokalnej spójności opisaną poniżej, ale też
nie dowodzi, że do pozycji prowadzi legalna sekwencja od ustawienia początkowego.

## Reprezentacja stanu

`State` jest prywatnym rekordem `ChessGame`. Współdzieli niemutowalne pozycje,
a mapy i listy mają kopie ochronne. `GameStatus` opisuje sytuację króla
(ONGOING/CHECK/CHECKMATE/STALEMATE). `GameResult` opisuje wynik całej partii.
Po rezygnacji status pozycji może nadal być ONGOING: o możliwości gry decyduje
`result.finished()`. `GameStatus.terminal()` oznacza tylko mata/pata.

## Kontrakty i przepływ

| Operacja | Wynik i skutek |
| --- | --- |
| `ChessRules.evaluate(position, move)` | `Accepted(Position)` lub `Rejected(Reason)`, bez zmiany wejścia |
| `ChessRules.after(position, move)` | Ta sama walidacja, ale odmowa zamieniona na `IllegalMoveException`; nie zna zakończeń historycznych |
| `legalMoves` / `hasLegalMove` w regułach | Lista niemutowalna / test istnienia; bez historycznych remisów |
| `ChessGame.play(move)` | Nowa pozycja, historia, licznik wystąpień i ewentualny wynik; jeden punkt publikacji |
| `claimDraw(player, claim)` | Zgłasza dostępne prawo dla strony mającej ruch; plansza i lista ruchów bez zmiany |
| `claimDraw(player, claim, intended)` | Sprawdza legalny kandydat i przyszły próg, ale nie wykonuje ruchu ani nie dopisuje go do historii |
| `resign(player)` | Dowolna strona może zrezygnować; przeciwnik zostaje zwycięzcą |
| `undo()` | Przywraca poprzednią zaakceptowaną komendę: ruch, rezygnację albo zgłoszenie; brak redo |
| `FenCodec.parse/format` | Pozycja ↔ sześciopolowy zapis; nowa `ChessGame(position)` zaczyna licznik wystąpień od 1 |
| `Perft.count/divide` | Drzewo półruchów / gałęzie pierwszego ruchu; nie uwzględnia końca przez historyczny remis/rezygnację |

Kolejność odmów ruchu: zakończona gra → to samo pole → puste źródło → zły kolor →
własna figura na celu → zbicie króla → promocja → geometria/droga/prawa specjalne →
bezpieczeństwo króla. Przy wielu naruszeniach widoczny jest pierwszy kod.
`evaluate` nie sprawdza zakończenia gry, bo nie posiada historii.
Roszada z szacha/przez szach daje `CASTLING_FORBIDDEN`, na atakowany cel —
`KING_IN_CHECK`. Kody ruchów opisuje
[IllegalMoveException](src/main/java/pl/jakubtworek/lld/chess/domain/IllegalMoveException.java).

Zgłoszenie sprawdza aktywność gry, uprawnienie strony, legalność zamierzonego
ruchu (jeśli podany) i dostępność żądanego prawa. Inne odmowy cyklu życia to
`GAME_OVER`, `WRONG_PLAYER`, `DRAW_NOT_AVAILABLE`, `NO_HISTORY` z
[GameCommandException](src/main/java/pl/jakubtworek/lld/chess/domain/GameCommandException.java).
`null` jest błędem programistycznym (`NullPointerException`), a wadliwy tekst
wartości lub FEN — `IllegalArgumentException`. Odmowy nigdy nie publikują stanu.

### Krytyczny scenariusz: trzecie wystąpienie z zamierzonym ruchem

Wykonaj `g1 f3`, `g8 f6`, `f3 g1`, `f6 g8`, potem jeszcze pierwsze trzy
z tych ruchów. Czarne mają ruch, a zamierzone f6-g8 dałoby trzecią pozycję startową.

1. `claimDraw(BLACK, THREEFOLD_REPETITION, f6-g8)` sprawdza stronę i legalność.
2. `PositionKey` kandydata ignoruje zmienione liczniki. Zachowane prawa roszady
   i brak legalnego en passant zgadzają się z poprzednimi wystąpieniami.
3. Mapa zawiera dwa wystąpienia; kandydat daje trzecie. Gra tworzy wynik remisu.
4. Pozycja nadal ma skoczka f6, tura pozostaje czarnych, historia ma 7 ruchów.
5. Undo wraca do dokładnie tego samego wcześniejszego snapshotu i mapy.

Przy niepoprawnym zgłoszeniu odrzucony jest tylko kandydat. Dla kontrprzykładu
związanego en passant: król h5, biały pion g5, czarny pion f5 i wieża a5.
Próba g5-f6 usunęłaby oba piony z piątej linii i odsłoniła króla. Taki cel
występuje w FEN, lecz **nie różnicuje klucza powtórzeń**.

### FEN: trzy poziomy gwarancji

Parser wymaga 8 rzędów po 8 pól, znanych symboli figur, tury w/b, praw w kolejności
KQkq lub `-`, celu a1..h8 lub `-`, nieujemnego licznika półruchów i dodatniego
numeru pełnego ruchu. Zapis cyfr jest kanoniczny (bez znaków i zer wiodących),
sąsiednie cyfry pustych pól są odrzucane. Liczniki mieszczą się w `long`;
przekroczenie zakresu podczas parsowania lub inkrementacji jest jawnym błędem,
nie zawinięciem. Ekstremalne liczniki mogą uniemożliwić również analizę kandydatów.

Spójność lokalna obejmuje króle i piony, zgodność praw z królem i wieżą na
polach początkowych, stronę/rząd celu en passant, obecność przesuniętego piona,
puste źródło i cel, zerowy licznik po podwójnym ruchu oraz brak szacha króla
strony niemającej ruchu. Parser nie wymaga sąsiedniego piona zdolnego bić:
FEN zapisuje cel również bez dostępnego bicia. Nie analizuje całej historii,
liczby koniecznych promocji ani osiągalności. Import nie przenosi wyniku ani
wcześniejszych wystąpień pozycji. `load` tworzy nową grę dopiero po pełnym sukcesie.

## Uruchomienie

Java 21; z katalogu low-level-design/chess:

~~~powershell
.\mvnw.cmd --batch-mode --no-transfer-progress clean verify
& "$env:JAVA_HOME/bin/java.exe" -jar target/chess-1.0.0-SNAPSHOT.jar
~~~

Konsola przyjmuje ruchy współrzędnymi, board, moves, claims, fen, `load FEN`, new, undo i resign. Zgłoszenia: `claim white threefold [MOVE]` albo `claim white fifty [MOVE]`, analogicznie dla black. `perft 0..4` i `divide 1..4` analizują pozycję; help pokazuje składnię, q lub EOF kończy demo.

## Testy i weryfikacja

[Testy](src/test/java/pl/jakubtworek/lld/chess) obejmują roszadę przez atakowane pole, związania, en passant odsłaniające szacha, promocję, FEN, remisy i undo. AttackIndexTest porównuje maski z niezależnym skanowaniem figur dla wygenerowanych plansz. Testy perft zestawiają generator ze znanymi liczebnościami drzew.

| Wymaganie | Testy zachowania | Granica gwarancji |
| --- | --- | --- |
| R1–R5 | `openingRefusals`, `pinnedPieceCannotExposeItsKing`, `castlingMovesBothPiecesAndLosesBothRights`, `promotionIsOneAtomicMoveWithExplicitChoice`, `enPassantMustRemoveVictimBeforeCheckingKingSafety` | Wybrane kontrprzykłady i parametry obu kolorów/skrzydeł, nie wszystkie pozycje |
| R6 | `optimizedGeneratorMatchesExhaustiveIntentOracleOnReferencePositions`, `initialMoveSetContainsOnlyPawnStepsAndKnightJumps` | Wyczerpująca enumeracja intencji dla czterech pozycji; wspólny walidator |
| R7 | `roundTripPreservesAllSixFields`, `malformedOrLocallyInconsistentFenIsRejected`, `invalidLoadLeavesPreviousGameAndUndoRestoresOriginalFen` | Składnia i wskazane warunki lokalne, nie osiągalność |
| R8 | `kiwipeteIncludesCastlingAndEnPassantBranches`, `promotionReferenceIncludesChecksAndUnderpromotions`, `divideMatchesTotalAndHandCountedOpeningBranches` | Zewnętrzne sumy i wybrane gałęzie; błędy mogą się kompensować |
| R9 | `thirdOccurrenceGivesClaimButDoesNotEndGame`, `intendedMoveMayCreateThirdOccurrenceWithoutBeingPlayed`, `halfmoveThresholdsDistinguishClaimFromAutomaticDraw`, `reaching150HalfmovesAutomaticallyDrawsButMateTakesPrecedence` | Bieżące i zapowiedziane zgłoszenia; bez procedur arbitra |
| R10 | `mateAndStalemateHaveExplicitResults`, `resultCannotContradictItsTermination`, `resignationIsIndependentOfTurnAndUndoReopensSamePosition` | Wynik i przykłady zakończeń; uproszczona rezygnacja |
| R11 | `undoCastlingRestoresBothPiecesRightsAndCounters`, `undoEnPassantAndDoubleStepRestoresWindowVictimTurnAndFullmove`, `undoPromotionRestoresPawnAndCapturedPiece`, `abandonedBranchDoesNotInflateFutureRepetitions` | Cofanie całych wersji i odcięcie gałęzi; bez redo |

| Pozycja | Głębokości → liście |
| --- | --- |
| Początkowa | 1 → 20, 2 → 400, 3 → 8902 |
| Kiwipete (CPW 2) | 1 → 48, 2 → 2039, 3 → 97862 |
| Końcówka wieżowo-pionowa (CPW 3) | 1 → 14, 2 → 191, 3 → 2812, 4 → 43238 |
| Promocje i obrona przed szachem (CPW 4) | 1 → 6, 2 → 264, 3 → 9467 |

## Ograniczenia

Brak AI, zegara szachowego, zapisu PGN i pełnego rozpoznawania martwych pozycji. Import FEN nie odtwarza wcześniejszych powtórzeń partii.
