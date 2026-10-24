package pl.jakubtworek.lld.logging.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * Wynik jednej nazwanej trasy, bez przechowywania mutowalnego wyjątku.
 * FAILED ma strukturalny opis typu i komunikatu; FILTERED różni się od NOT_ATTEMPTED po STOP.
 * WRITTEN oznacza powrót sinka, nie brak duplikatów lub trwałość.
 */
public record Delivery(String target, Status status, Optional<DeliveryFailure> failure) {

    public enum Status {

        FILTERED, WRITTEN, FAILED, NOT_ATTEMPTED
    }

    public Delivery {
        Objects.requireNonNull(target);
        Objects.requireNonNull(status);
        Objects.requireNonNull(failure);
        if ((status == Status.FAILED) != failure.isPresent())
            throw new IllegalArgumentException("Niespójna odmowa");
    }
}
