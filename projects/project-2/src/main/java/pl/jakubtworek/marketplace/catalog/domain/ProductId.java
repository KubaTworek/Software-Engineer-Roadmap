package pl.jakubtworek.marketplace.catalog.domain;

import java.util.UUID;

public record ProductId(UUID value) {
    public static ProductId newId() { return new ProductId(UUID.randomUUID()); }
    public static ProductId of(UUID value) { return new ProductId(value); }
}
