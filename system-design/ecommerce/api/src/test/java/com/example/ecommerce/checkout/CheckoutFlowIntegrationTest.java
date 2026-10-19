package com.example.ecommerce.checkout;

import com.example.ecommerce.auth.AppUser;
import com.example.ecommerce.auth.AppUserRepository;
import com.example.ecommerce.auth.UserRole;
import com.example.ecommerce.cart.CartService;
import com.example.ecommerce.cart.dto.CartDtos;
import com.example.ecommerce.catalog.Category;
import com.example.ecommerce.catalog.CategoryRepository;
import com.example.ecommerce.catalog.Product;
import com.example.ecommerce.catalog.ProductRepository;
import com.example.ecommerce.catalog.ProductVariant;
import com.example.ecommerce.checkout.dto.CheckoutDtos;
import com.example.ecommerce.inventory.InventoryItemRepository;
import com.example.ecommerce.inventory.InventoryService;
import com.example.ecommerce.order.CustomerOrderRepository;
import com.example.ecommerce.payment.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Przekrojowy dowód lokalnej granicy transakcji: drugi identyczny request nie
 * tworzy orderu, paymentu ani kolejnej rezerwacji.
 */
@SpringBootTest(properties = {
        "app.inventory.expiration-worker.enabled=false",
        "app.catalog-cache.warmer-enabled=false",
        "app.outbox.publisher-enabled=false"
})
@Transactional
class CheckoutFlowIntegrationTest {
    @Autowired AppUserRepository users;
    @Autowired CategoryRepository categories;
    @Autowired ProductRepository products;
    @Autowired InventoryService inventory;
    @Autowired InventoryItemRepository inventoryItems;
    @Autowired CartService carts;
    @Autowired CheckoutService checkout;
    @Autowired CustomerOrderRepository orders;
    @Autowired PaymentRepository payments;

    @Test
    void retryReturnsTheSameResultAndKeepsOneReservation() {
        String suffix = UUID.randomUUID().toString();
        AppUser user = users.save(new AppUser(
                "checkout-" + suffix + "@example.com", "hash", "Checkout Test", Set.of(UserRole.CUSTOMER)));
        Category category = categories.save(new Category("Test", "test-" + suffix, null));
        Product product = new Product("P-" + suffix, "Product", "product-" + suffix, "", "Test", category);
        ProductVariant variant = new ProductVariant("SKU-" + suffix, "Default", new BigDecimal("99.99"), "PLN");
        product.addVariant(variant);
        products.saveAndFlush(product);
        inventory.createInventoryItem(variant, 2);
        carts.addItem(user, new CartDtos.AddCartItemRequest(variant.getId(), 1));

        long orderCount = orders.count();
        long paymentCount = payments.count();
        var request = new CheckoutDtos.CheckoutRequest("Shipping address", "Billing address", "COURIER");
        var first = checkout.checkout(user, "key-" + suffix, request);
        var retry = checkout.checkout(user, "key-" + suffix, request);

        assertThat(retry.order().id()).isEqualTo(first.order().id());
        assertThat(retry.payment().id()).isEqualTo(first.payment().id());
        assertThat(orders.count()).isEqualTo(orderCount + 1);
        assertThat(payments.count()).isEqualTo(paymentCount + 1);
        assertThat(inventoryItems.findByVariantId(variant.getId()).orElseThrow().getReservedQuantity())
                .isEqualTo(1);
    }
}
