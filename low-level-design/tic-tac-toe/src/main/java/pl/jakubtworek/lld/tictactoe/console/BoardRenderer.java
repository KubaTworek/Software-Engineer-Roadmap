package pl.jakubtworek.lld.tictactoe.console;

import pl.jakubtworek.lld.tictactoe.Board;
import pl.jakubtworek.lld.tictactoe.Position;

/**
 * Prezentacja planszy jest odpowiedzialnością adaptera konsoli. Domena nie
 * powinna znać kropek, odstępów ani System.out: te same obiekty można wtedy
 * pokazać w innym interfejsie bez zmiany reguł gry.
 * Cel edukacyjny: prezentacja może przekształcać dane bez stawania się ich
 * właścicielem. Ta klasa buduje String i nigdy nie wykonuje play ani I/O.
 * Test rendererAlignsTwoDigitCoordinatesWithoutChangingSnapshot pokazuje,
 * że formatowanie nie zmienia widoku nawet przy współrzędnych dwucyfrowych.
 */
public final class BoardRenderer {

    public String render(Board board) {
        // Szerokość wynika z największej współrzędnej N-1, nie ze znaku gracza.
        // Dzięki temu kolumny pozostają wyrównane po przejściu z 9 do 10.
        // Oglądamy N*N pól: render ma koszt O(N^2), niezależnie od O(N) ruchu.
        int width = Integer.toString(board.size() - 1).length();
        var text = new StringBuilder(" ".repeat(width + 1));
        for (int column = 0; column < board.size(); column++) {
            text.append(padded(Integer.toString(column), width)).append(' ');
        }
        text.append('\n');
        for (int row = 0; row < board.size(); row++) {
            text.append(padded(Integer.toString(row), width)).append(' ');
            for (int column = 0; column < board.size(); column++) {
                // To adapter wybiera symbol pustego pola. Domena zwraca brak
                // znaku jako Optional, więc nie zależy od konsolowej kropki.
                String mark = board.markAt(new Position(row, column)).map(Enum::name).orElse(".");
                text.append(padded(mark, width)).append(' ');
            }
            text.append('\n');
        }
        return text.toString();
    }

    private static String padded(String value, int width) {
        return " ".repeat(width - value.length()) + value;
    }
}
