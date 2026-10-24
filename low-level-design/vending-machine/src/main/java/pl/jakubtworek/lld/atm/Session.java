package pl.jakubtworek.lld.atm;

import java.util.Objects;
import java.time.Instant;

/**
 * Trzy jawne warianty zamiast pól nullable typu card/account/authenticated.
 * CardInserted przechowuje tylko liczbę błędów tej sesji, nigdy PIN. Po trzecim
 * błędzie wracamy do Idle, więc nie istnieje wariant z licznikiem 3 lub 4.
 * Active współdzieli tożsamość i niezmienny termin ważności. Uwierzytelnienie
 * zachowuje te metadane; nowy token i nowy termin powstają dopiero przy insert.
 */
public sealed interface Session {

    record Idle() implements Session {
    }

    sealed interface Active extends Session {

        SessionId id();

        CardId card();

        Instant expiresAt();
    }

    record CardInserted(SessionId id, CardId card, int failedAttempts, Instant expiresAt) implements Active {

        public CardInserted {
            Objects.requireNonNull(card, "card");
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(expiresAt, "expiresAt");
            if (failedAttempts < 0 || failedAttempts > 2) {
                throw new IllegalArgumentException("Licznik prób: 0..2");
            }
        }
    }

    record Authenticated(SessionId id, CardId card, AccountId account, Instant expiresAt) implements Active {

        public Authenticated {
            Objects.requireNonNull(card, "card");
            Objects.requireNonNull(account, "account");
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(expiresAt, "expiresAt");
        }
    }
}
