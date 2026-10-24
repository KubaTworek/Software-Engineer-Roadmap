package pl.jakubtworek.lld.atm;

/**
 * Granica weryfikacji karty i PIN-u. Nie zmienia kont ani kasety i nie wraca
 * rekurencyjnie do Atm. false jest błędną próbą, wyjątek awarią weryfikacji.
 * Zakres ćwiczenia to synchroniczny, lokalny adapter bez zewnętrznych skutków.
 * Podłączenie sieci nie da automatycznie atomowości ani odporności na timeouty.
 */
public interface CardAuthenticator {

    boolean authenticate(CardId card, Pin pin);
}
