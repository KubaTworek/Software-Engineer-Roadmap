package pl.jakubtworek.marketplace.ordering.application;

import pl.jakubtworek.marketplace.shared.kernel.Money;
import java.util.UUID;

/**
 * Jawny kontrakt potrzebny use case'owi Ordering. Nie zwracamy agregatu Product z
 * obcego bounded contextu: Ordering potrzebuje tylko zatwierdzonej ceny. Dzięki
 * temu klient HTTP nie może sam zadeklarować ceny, a moduł nie wiąże się z modelem
 * wewnętrznym Catalog.
 */
public interface ProductPricingPort {
    Money currentPrice(UUID productId);
}
