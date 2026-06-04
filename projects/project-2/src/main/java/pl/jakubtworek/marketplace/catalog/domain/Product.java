package pl.jakubtworek.marketplace.catalog.domain;

import pl.jakubtworek.marketplace.shared.kernel.Money;

/**
 * Agregat reprezentujący produkt w module Catalog.
 *
 * Product jest częścią modelu domenowego, więc nie powinien zależeć od Springa,
 * JPA, HTTP ani żadnego innego szczegółu infrastrukturalnego.
 *
 * Odpowiedzialności tej klasy:
 * - przechowywanie tożsamości produktu,
 * - pilnowanie podstawowych reguł dotyczących produktu,
 * - udostępnianie zachowań biznesowych, takich jak zmiana ceny lub dezaktywacja.
 */
public class Product {

    /**
     * Tożsamość produktu.
     *
     * Identyfikator jest niemutowalny — produkt nie powinien zmieniać swojej tożsamości
     * po utworzeniu.
     */
    private final ProductId id;

    /**
     * Nazwa produktu.
     *
     * Jest mutowalna technicznie, ale obecnie nie ma metody biznesowej do jej zmiany.
     * Jeśli w przyszłości pojawi się zmiana nazwy, warto dodać metodę typu rename(...),
     * zamiast wystawiać setter.
     */
    private String name;

    /**
     * Cena produktu jako value object.
     *
     * Money powinno pilnować poprawności kwoty i waluty, np. czy kwota nie jest ujemna
     * oraz czy waluta jest poprawna.
     */
    private Money price;

    /**
     * Status produktu.
     *
     * Status pozwala odróżnić produkt aktywny od nieaktywnego bez usuwania go z systemu.
     */
    private ProductStatus status;

    /**
     * Prywatny konstruktor wymusza tworzenie produktu przez nazwane metody fabrykujące.
     *
     * Dzięki temu kod z zewnątrz nie tworzy produktu w przypadkowy sposób.
     */
    private Product(ProductId id, String name, Money price, ProductStatus status) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("product name cannot be blank");
        }

        this.id = id;
        this.name = name;
        this.price = price;
        this.status = status;
    }

    /**
     * Tworzy nowy produkt biznesowy.
     *
     * Nowy produkt:
     * - dostaje nowe ID,
     * - ma podaną nazwę i cenę,
     * - startuje jako aktywny.
     *
     * Ta metoda jest używana przy normalnym tworzeniu produktu przez use case.
     */
    public static Product create(String name, Money price) {
        return new Product(
                ProductId.newId(),
                name,
                price,
                ProductStatus.ACTIVE
        );
    }

    /**
     * Odtwarza produkt z istniejącego stanu, np. z bazy danych.
     *
     * Ta metoda nie oznacza utworzenia nowego produktu biznesowo.
     * Służy do rekonstrukcji obiektu domenowego na podstawie danych zapisanych wcześniej.
     *
     * Różnica względem create(...):
     * - create(...) generuje nowe ID i ustawia produkt jako ACTIVE,
     * - restore(...) przyjmuje istniejące ID i istniejący status.
     */
    public static Product restore(ProductId id, String name, Money price, ProductStatus status) {
        return new Product(
                id,
                name,
                price,
                status
        );
    }

    /**
     * Zmienia cenę produktu.
     *
     * To jest zachowanie domenowe, a nie zwykły setter.
     * Dzięki temu w przyszłości można tu dodać reguły, np.:
     * - nie można zmienić ceny produktu nieaktywnego,
     * - nowa cena nie może być taka sama jak obecna,
     * - zmiana ceny może emitować event domenowy.
     */
    public void changePrice(Money newPrice) {
        this.price = newPrice;
    }

    /**
     * Dezaktywuje produkt.
     *
     * Produkt nie jest usuwany z systemu, tylko zmienia status na INACTIVE.
     * To pozwala zachować historię zamówień i odwołania do produktu.
     */
    public void deactivate() {
        this.status = ProductStatus.INACTIVE;
    }

    /**
     * Zwraca identyfikator produktu.
     */
    public ProductId id() {
        return id;
    }

    /**
     * Zwraca nazwę produktu.
     */
    public String name() {
        return name;
    }

    /**
     * Zwraca aktualną cenę produktu.
     */
    public Money price() {
        return price;
    }

    /**
     * Zwraca aktualny status produktu.
     */
    public ProductStatus status() {
        return status;
    }
}