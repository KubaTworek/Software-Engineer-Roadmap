package pl.jakubtworek.lld.tictactoe;

/**
 * Niemutowalna konfiguracja wariantu N x N, w którym wygrywa co najmniej K
 * kolejnych znaków. Klasyczne 3 x 3 / K=3 jest jednym z wariantów, a nie
 * szczególną ścieżką algorytmu. Reguł nie zmieniamy w trakcie partii.
 *
 * Cel edukacyjny: konfiguracja też ma niezmienniki. K większe od N dawałoby
 * wariant bez możliwej wygranej, a N=0 planszę bez ruchów. Odrzucamy je
 * przy tworzeniu wartości zamiast zmuszać każdy algorytm do ich obsługi.
 * Record jest tutaj odpowiedni: ma tylko składowe int, więc nie przechowuje
 * referencji do cudzej mutowalnej struktury. Sprawdź rejectsImpossibleConfiguration.
 */
public record GameRules(int boardSize, int winningLength) {

    // Jawny limit laboratorium ogranicza pamięć i koszt kopiowania planszy.
    // Nie jest to reguła matematyczna gry ani deklaracja skali produkcyjnej.
    public static final int MAX_BOARD_SIZE = 100;

    public GameRules {
        // Walidacja dotyczy relacji pomiędzy parametrami jednego obiektu.
        // Po udanym utworzeniu konfiguracji pozostały kod może zakładać 1<=K<=N.
        if (boardSize < 1 || boardSize > MAX_BOARD_SIZE) {
            throw new IllegalArgumentException("Rozmiar planszy musi należeć do zakresu 1..100");
        }
        if (winningLength < 1 || winningLength > boardSize) {
            throw new IllegalArgumentException("Długość zwycięskiej linii musi należeć do zakresu 1..N");
        }
    }

    public static GameRules classic() {
        // Fabryka nazywa wariant domenowy. Dzięki temu klient nie musi pamiętać,
        // która z dwóch liczb 3 oznacza rozmiar, a która długość zwycięskiej linii.
        return new GameRules(3, 3);
    }
}
