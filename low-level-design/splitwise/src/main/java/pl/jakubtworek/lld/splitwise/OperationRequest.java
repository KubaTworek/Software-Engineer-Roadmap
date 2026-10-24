package pl.jakubtworek.lld.splitwise;

import java.util.Map;
import java.util.Objects;

/**
 * Kanoniczna treść polecenia do porównania retry. Dla wydatku porównujemy
 * zapisane udziały, nie klasę strategii ani kolejność mapy. Dwie strategie
 * dające te same grosze mają tu ten sam efekt. Nie stosujemy skrótu hash jako
 * dowodu równości: recordy porównują pełne, niemutowalne wartości.
 */
sealed interface OperationRequest {

    record ExpenseRequest(MemberId payer, Money total, Map<MemberId, Money> shares,
            String description) implements OperationRequest {

        public ExpenseRequest {
            shares = Map.copyOf(shares);
            description = text(description);
        }
    }

    record SettlementRequest(MemberId from, MemberId to, Money amount) implements OperationRequest {
    }

    record ReversalRequest(MemberId actor, EntryId expenseId, String reason) implements OperationRequest {

        public ReversalRequest {
            reason = text(reason);
        }
    }

    private static String text(String value) {
        String normalized = Objects.requireNonNull(value, "value").strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Opis nie może być pusty");
        }
        return normalized;
    }
}

/**
 * Pakietowy zapis pary żądanie–wynik. Trafia do tego samego kandydata co wpis dziennika.
 */
record RecordedOperation(OperationRequest request, LedgerEntry result) {
}
