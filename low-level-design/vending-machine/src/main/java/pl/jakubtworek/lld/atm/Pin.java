package pl.jakubtworek.lld.atm;

import java.util.Arrays;
import java.util.Objects;

/**
 * Wartość czterech cyfr, z kopią tablicy i maskowanym toString. Nie umieszczamy
 * jej w snapshotach ani potwierdzeniach. matches służy tylko adapterowi demo:
 * nie jest hashowaniem, weryfikacją sprzętową ani gwarancją wymazania z pamięci.
 */
public final class Pin {

    private final char[] digits;

    public Pin(char[] digits) {
        this.digits = Objects.requireNonNull(digits, "digits").clone();
        if (this.digits.length != 4) {
            throw new IllegalArgumentException("PIN wymaga czterech cyfr ASCII");
        }
        for (char digit : this.digits) {
            if (digit < '0' || digit > '9') {
                throw new IllegalArgumentException("PIN wymaga czterech cyfr ASCII");
            }
        }
    }

    public static Pin parse(String text) {
        return new Pin(Objects.requireNonNull(text, "text").toCharArray());
    }

    public boolean matches(Pin other) {
        return Arrays.equals(digits, Objects.requireNonNull(other, "other").digits);
    }

    @Override
    public String toString() {
        return "****";
    }
}
