package pl.jakubtworek.lld.atm.console;

import pl.jakubtworek.lld.atm.*;
import java.util.Map;
import java.util.Objects;

/**
 * Lokalny adapter z fikcyjnymi danymi, do testów i demo. Przechowuje PIN-y
 * wprost jako wartości w pamięci; nie przedstawiamy tego jako zabezpieczenia
 * bankowego. Interfejs umożliwia wstrzyknięcie także awarii weryfikacji w teście.
 */
public final class DemoAuthenticator implements CardAuthenticator {

    private final Map<CardId, Pin> pins;

    public DemoAuthenticator(Map<CardId, Pin> pins) {
        this.pins = Map.copyOf(pins);
    }

    @Override
    public boolean authenticate(CardId card, Pin pin) {
        Objects.requireNonNull(card, "card");
        Objects.requireNonNull(pin, "pin");
        var expected = pins.get(card);
        return expected != null && expected.matches(pin);
    }
}
