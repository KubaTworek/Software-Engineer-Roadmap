package pl.jakubtworek.lld.chess.domain;

import java.util.Objects;

/**
 * Wynik sprawdzenia intencji. Odmowa jest normalną wartością podczas analizy
 * kandydatów, więc generator nie tworzy stosów wyjątków. Dwa warianty sealed
 * uniemożliwiają wynik „jednocześnie sukces i błąd” lub „żaden z nich”.
 */
public sealed interface MoveEvaluation {

    record Accepted(Position position) implements MoveEvaluation {

        public Accepted {
            Objects.requireNonNull(position);
        }
    }

    record Rejected(IllegalMoveException.Reason reason) implements MoveEvaluation {

        public Rejected {
            Objects.requireNonNull(reason);
        }
    }
}
