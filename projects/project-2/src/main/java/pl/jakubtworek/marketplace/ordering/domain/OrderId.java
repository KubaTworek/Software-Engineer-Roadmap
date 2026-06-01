package pl.jakubtworek.marketplace.ordering.domain;

import java.util.UUID;

public record OrderId(UUID value) {
    public static OrderId newId() { return new OrderId(UUID.randomUUID()); }
    public static OrderId of(UUID value) { return new OrderId(value); }
}
