package pl.jakubtworek.lld.tictactoe;

import java.util.Objects;
import java.util.Optional;
import static pl.jakubtworek.lld.tictactoe.IllegalMoveException.Reason.*;

/**
 * Granica spójności jednej partii: wyłącznie ten obiekt przyjmuje polecenia
 * ruchu, pilnuje kolejności i podejmuje decyzję o zakończeniu gry.
 * Plansza odpowiada za pola, a WinningLines za geometrię; gra koordynuje te
 * odpowiedzialności zamiast kopiować ich logikę.
 *
 * Cel edukacyjny: zobaczyć granicę spójności, czyli miejsce, przez które muszą
 * przejść wszystkie zmiany mające zachować wspólną regułę. Nie potrzebujemy
 * frameworka, aby zamknąć planszę, turę i wynik w jednym spójnym przepływie.
 * Test rejectedMovesPreserveEntireStateAndDoNotConsumeTurn sprawdza odmowę,
 * a winningOnLastFreeCellTakesPrecedenceOverDraw — kolejność rozstrzygnięć.
 *
 * Kontrakt jest JEDNOWĄTKOWY. Niemutowalność widoków nie sprawia, że równoległe
 * play są bezpieczne: dwa wątki mogłyby odczytać tę samą turę i nadpisać wynik.
 * W aplikacji sieciowej serializacja poleceń dla partii byłaby osobnym wymaganiem.
 */
public final class TicTacToeGame {

    private final GameRules rules;
    private GameSnapshot state;

    public TicTacToeGame() {
        this(GameRules.classic());
    }

    public TicTacToeGame(GameRules rules) {
        // Nowa instancja to nowa partia. Nie udostępniamy konstruktora z dowolną
        // planszą, bo import wymagałby sprawdzenia historii lub legalności pozycji.
        this.rules = Objects.requireNonNull(rules, "rules");
        state = new GameSnapshot(Board.empty(rules.boardSize()), GameStatus.IN_PROGRESS, Optional.of(Mark.X));
    }

    /**
     * Oddaje bezpieczny widok bez kopiowania całej planszy przy każdym odczycie.
     * Klient może go zachować, ale sam model nie zapamiętuje historii.
     */
    public GameSnapshot snapshot() {
        return state;
    }

    /**
     * Przyjmuje jedno polecenie. Sukces publikuje nowy widok; odmowa domenowa
     * zachowuje dokładnie poprzedni. Kolejność walidacji jest częścią kontraktu:
     * null, zakończona partia, nie ta tura, poza planszą, zajęte pole.
     * Argument player oznacza deklarowany znak, nie zweryfikowaną tożsamość osoby.
     */
    public GameSnapshot play(Mark player, Position position) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(position, "position");
        // Najpierw reguły całej partii, potem lokalna reguła pola w Board.
        // Dzięki temu ruch na zajęte pole PO wygranej daje GAME_FINISHED.
        // Błąd nie jest losowym skutkiem kolejności wywołań pomocniczych.
        if (state.status() != GameStatus.IN_PROGRESS) {
            throw new IllegalMoveException(GAME_FINISHED, "Partia została zakończona");
        }
        if (state.nextPlayer().orElseThrow() != player) {
            throw new IllegalMoveException(WRONG_TURN, "Teraz ruch drugiego gracza");
        }
        // Najpierw powstaje kandydat na nowy stan. Board waliduje granice
        // i zajętość PRZED utworzeniem zmienionej planszy. Żaden błąd domenowy
        // nie zużywa tury ani nie wymaga odkręcania częściowo wykonanego ruchu.
        Board nextBoard = state.board().afterMove(player, position);
        GameStatus nextStatus;
        if (WinningLines.completesLine(nextBoard, position, player, rules.winningLength())) {
            nextStatus = GameStatus.wonBy(player);
        } else if (nextBoard.isFull()) {
            // Zwycięstwo ma pierwszeństwo przed remisem: ostatnie wolne pole
            // może jednocześnie zapełnić planszę i domknąć zwycięską linię.
            nextStatus = GameStatus.DRAW;
        } else {
            nextStatus = GameStatus.IN_PROGRESS;
        }
        Optional<Mark> nextPlayer = nextStatus == GameStatus.IN_PROGRESS ? Optional.of(player.opponent()) : Optional.empty();
        GameSnapshot nextState = new GameSnapshot(nextBoard, nextStatus, nextPlayer);
        // Jedyny punkt publikacji poprawnego ruchu: plansza, wynik i kolejna
        // tura stają się jednym spójnym widokiem. To nie transakcja bazodanowa
        // ani synchronizacja wątków, tylko lokalna ochrona niezmiennika.
        state = nextState;
        return state;
    }
}
