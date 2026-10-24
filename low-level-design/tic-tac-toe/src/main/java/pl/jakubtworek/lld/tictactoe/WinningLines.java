package pl.jakubtworek.lld.tictactoe;

/**
 * Bezstanowy algorytm geometrii zwycięstwa. Nie wykonuje ruchów i nie zmienia
 * tury. Jest szczegółem pakietu: publiczny kontrakt gry nie zależy od sposobu
 * liczenia linii. Nie potrzebujemy interfejsu Strategy dla jednego algorytmu.
 * Cel edukacyjny: wyprowadzić optymalizację z niezmiennika, zamiast jedynie
 * zapamiętać cztery kierunki. Sam algorytm kosztuje O(K log W), bo sprawdza stałą
 * liczbę osi ograniczonych K, z odczytem bitowego drzewa W słów. Testy wszystkich historii
 * klasycznych i generowanych partii porównują go z pełnym skanowaniem.
 */
final class WinningLines {

    private WinningLines() {
    }

    static boolean completesLine(Board board, Position lastMove, Mark mark, int length) {
        // Przed ruchem partia była aktywna, czyli nie zawierała zwycięskiej
        // linii. Zmieniło się jedno pole. Każde NOWE zwycięstwo musi zatem
        // przechodzić przez to pole. Założenie jest poprawne dla plansz
        // tworzonych przez grę; nie jest walidatorem arbitralnego importu pozycji.
        // (dr,dc) opisuje zmianę wiersza i kolumny: poziom, pion, dwie przekątne.
        // Wystarczą cztery osie, bo lineLength bada każdą w obu kierunkach.
        // >= pozwala zaakceptować domknięcie luki tworzące więcej niż K znaków.
        return lineLength(board, lastMove, mark, 0, 1, length) >= length || lineLength(board, lastMove, mark, 1, 0,
                length) >= length || lineLength(board, lastMove, mark, 1, 1, length) >= length || lineLength(board,
                lastMove, mark, 1, -1, length) >= length;
    }

    private static int lineLength(Board board, Position origin, Mark mark, int dr, int dc, int limit) {
        // Liczymy dwie połówki osi i dokładnie raz pole ostatniego ruchu.
        // To ważne, gdy ostatni znak wypełnia środek linii, np. X X _ X X.
        // Sprawdzenie wyłącznie „w prawo” przeoczyłoby takie zwycięstwo.
        return 1 + count(board, origin, mark, dr, dc, limit) + count(board, origin, mark, -dr, -dc, limit);
    }

    private static int count(Board board, Position origin, Mark mark, int dr, int dc, int limit) {
        int row = origin.row() + dr;
        int column = origin.column() + dc;
        int count = 0;
        // contains jest przed markAt: operator && nie wywoła odczytu poza
        // planszą. Krawędź kończy skanowanie, nie jest błędem polecenia gracza.
        while (count < limit - 1 && board.contains(row, column) && board.markAtUnchecked(row, column) == mark) {
            count++;
            row += dr;
            column += dc;
        }
        // Puste pole i znak przeciwnika przerywają ciąg. Samo zliczenie
        // wszystkich X w wierszu nie wystarcza: wygrana wymaga sąsiedztwa.
        return count;
    }
}
