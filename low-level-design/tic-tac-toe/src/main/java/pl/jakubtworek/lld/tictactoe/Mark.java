package pl.jakubtworek.lld.tictactoe;

/**
 * W tej grze tożsamość uczestnika przy stole określa jego znak: X albo O.
 * Profil użytkownika, logowanie i nazwa wyświetlana są inną odpowiedzialnością
 * i nie są potrzebne do sprawdzenia legalności ruchu. Enum wystarcza więc
 * zamiast hierarchii Player -> HumanPlayer / ComputerPlayer.
 * Cel edukacyjny: modelować tylko rozróżnienia potrzebne do reguł. Sposób
 * wyboru ruchu (człowiek lub AI) można umieścić poza znakiem, bo nie zmienia
 * legalności ruchu. Znak nie jest dowodem uwierzytelnienia osoby.
 */
public enum Mark {

    X, O;

    public Mark opponent() {
        // Reguła „drugi uczestnik” ma sens przy dokładnie dwóch znakach.
        // Dla większej liczby graczy potrzebna byłaby kolejność, a nie ten ternary.
        return this == X ? O : X;
    }
}
