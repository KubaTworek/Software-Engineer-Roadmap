package pl.jakubtworek.lld.carrental;

import java.util.Objects;

/**
 * Klient może rezerwować kilka aut; w tym ćwiczeniu nie ma limitów ani badania prawa jazdy.
 */
public record Customer(CustomerId id, String name) {

    public Customer {
        Objects.requireNonNull(id, "id");
        name = Text.nonBlank(name);
    }
}
