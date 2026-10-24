package pl.jakubtworek.lld.chess.domain;

/**
 * Sytuacja szachowa strony mającej ruch, wyliczana wyłącznie z pozycji.
 * Wynik całej gry należy do GameResult: rezygnacja/remis mogą zakończyć
 * również pozycję ONGOING lub CHECK. terminal dotyczy tylko mata/pata.
 */
public enum GameStatus {

    ONGOING, CHECK, CHECKMATE, STALEMATE;

    public boolean terminal() {
        return this == CHECKMATE || this == STALEMATE;
    }
}
