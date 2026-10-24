package pl.jakubtworek.lld.parkinglot;

import java.util.Objects;
import java.util.UUID;

/**
 * Tożsamość biletu jest złożona: parking + numer lokalny. Bilet nr 1 z innego
 * parkingu nie może zamknąć tutejszego biletu nr 1. Sama kolejna liczba nie
 * wystarczałaby do rozróżnienia tych dokumentów. Identyfikator nie jest jednak
 * mechanizmem uwierzytelnienia ani zabezpieczeniem przed sfałszowaniem żądania.
 */
public record TicketId(UUID parkingLotId, long number) {

    public TicketId {
        Objects.requireNonNull(parkingLotId, "parkingLotId");
        if (number < 1) {
            throw new IllegalArgumentException("Numer biletu musi być dodatni");
        }
    }
}
