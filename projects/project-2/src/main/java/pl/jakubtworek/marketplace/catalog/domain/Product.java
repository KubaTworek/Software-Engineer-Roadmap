package pl.jakubtworek.marketplace.catalog.domain;

import pl.jakubtworek.marketplace.shared.kernel.Money;

public class Product {
    private final ProductId id;
    private String name;
    private Money price;
    private ProductStatus status;

    private Product(ProductId id, String name, Money price, ProductStatus status) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("product name cannot be blank");
        this.id = id;
        this.name = name;
        this.price = price;
        this.status = status;
    }

    public static Product create(String name, Money price) {
        return new Product(ProductId.newId(), name, price, ProductStatus.ACTIVE);
    }

    public static Product restore(ProductId id, String name, Money price, ProductStatus status) {
        return new Product(id, name, price, status);
    }

    public void changePrice(Money newPrice) {
        this.price = newPrice;
    }

    public void deactivate() {
        this.status = ProductStatus.INACTIVE;
    }

    public ProductId id() { return id; }
    public String name() { return name; }
    public Money price() { return price; }
    public ProductStatus status() { return status; }
}
