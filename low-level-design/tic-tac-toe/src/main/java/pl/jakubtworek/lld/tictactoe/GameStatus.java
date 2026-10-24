package pl.jakubtworek.lld.tictactoe;

import java.util.Optional;

/**
 * Wynik zakodowany w jednym stanie eliminuje sprzeczność typu „remis ze
 * zwycięzcą”. Oddzielne mutowalne pola finished, draw i winner dopuszczałyby
 * kombinacje, które nie mają sensu w domenie.
 * Cel edukacyjny: tak dobrać reprezentację, aby część błędnych kombinacji
 * była niemożliwa do zapisania. Enum ogranicza wyniki do czterech wariantów,
 * lecz sam nie sprawdza, czy plansza odpowiada wybranemu wynikowi — to rola gry.
 */
public enum GameStatus {

    IN_PROGRESS, X_WON, O_WON, DRAW;

    public Optional<Mark> winner() {
        // Brak zwycięzcy nie oznacza automatycznie remisu: aktywna gra także
        // go nie ma. Klient ustala zakończenie ze statusu, nie tylko z Optional.
        return switch(this) {
            case X_WON ->
                Optional.of(Mark.X);
            case O_WON ->
                Optional.of(Mark.O);
            default ->
                Optional.empty();
        };
    }

    static GameStatus wonBy(Mark mark) {
        // Metoda jest szczegółem domeny i otrzymuje nie-null z play.
        // Powiązanie wyniku ze znakiem jest świadomym uproszczeniem dwóch stron;
        // dodanie trzeciej wymagałoby nowego modelu wyniku i kolejności uczestników.
        return mark == Mark.X ? X_WON : O_WON;
    }
}
