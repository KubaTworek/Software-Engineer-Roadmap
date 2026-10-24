package pl.jakubtworek.lld.tictactoe;

import java.util.Objects;
import java.util.Optional;

/**
 * Widok partii w konkretnym momencie. Board jest niemutowalny, więc odczytowe
 * metody nie udostępniają mutowalnego wnętrza. Klient może zachować ten widok
 * i zestawić jego pola z późniejszym bez ryzyka nadpisania wcześniejszej pozycji.
 * Nie jest to dziennik ruchów: model sam nie przechowuje poprzednich widoków.
 *
 * Konstruktor ma widoczność pakietową: klient spoza pakietu domeny może widok
 * tylko otrzymać od gry. Publiczny record wymagałby publicznego konstruktora,
 * pozwalającego zestawić dowolną planszę z wynikiem, np. pustą planszę z X_WON.
 * Klasa final zachowuje odczytowe API, ale ogranicza tworzenie do zaufanego
 * pakietu modelu. Nie udajemy, że sprawdzenie kilku pól potrafi udowodnić
 * legalność całej historii. To odpowiedzialność TicTacToeGame.
 *
 * Cel edukacyjny: odróżnić widok od właściciela stanu oraz kontrolę tworzenia
 * od pełnej walidacji pozycji. Klasa nie ma równości strukturalnej: == oznacza
 * ten sam opublikowany widok, a nie dowolne dwie identyczne plansze.
 * Test archivedSnapshotRemainsUnchangedAfterLaterMoves pokazuje stabilny odczyt.
 */
public final class GameSnapshot {

    private final Board board;
    private final GameStatus status;
    private final Optional<Mark> nextPlayer;

    GameSnapshot(Board board, GameStatus status, Optional<Mark> nextPlayer) {
        this.board = Objects.requireNonNull(board, "board");
        this.status = Objects.requireNonNull(status, "status");
        this.nextPlayer = Objects.requireNonNull(nextPlayer, "nextPlayer");
        // Oba zdania muszą mieć tę samą wartość logiczną: gra jest aktywna
        // wtedy i tylko wtedy, gdy istnieje kolejny gracz. To lokalny warunek
        // spójności; nie dowodzi np. poprawnej liczby znaków na planszy.
        if ((status == GameStatus.IN_PROGRESS) != nextPlayer.isPresent()) {
            throw new IllegalArgumentException("Tylko trwająca partia ma następnego gracza");
        }
    }

    public Board board() {
        return board;
    }

    public GameStatus status() {
        return status;
    }

    public Optional<Mark> nextPlayer() {
        return nextPlayer;
    }
}
