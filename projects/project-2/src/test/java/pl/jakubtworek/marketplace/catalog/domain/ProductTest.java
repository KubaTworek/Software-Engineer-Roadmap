package pl.jakubtworek.marketplace.catalog.domain;

import org.junit.jupiter.api.Test;
import pl.jakubtworek.marketplace.shared.kernel.Money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductTest {

    @Test
    void shouldCreateActiveProduct() {
        Product product = Product.create("Keyboard", Money.of("199.99", "PLN"));

        assertThat(product.id()).isNotNull();
        assertThat(product.name()).isEqualTo("Keyboard");
        assertThat(product.price()).isEqualTo(Money.of("199.99", "PLN"));
        assertThat(product.status()).isEqualTo(ProductStatus.ACTIVE);
    }

    @Test
    void shouldRejectBlankName() {
        assertThatThrownBy(() -> Product.create(" ", Money.of("199.99", "PLN")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("product name cannot be blank");
    }

    @Test
    void shouldChangePrice() {
        Product product = Product.create("Keyboard", Money.of("199.99", "PLN"));

        product.changePrice(Money.of("249.99", "PLN"));

        assertThat(product.price()).isEqualTo(Money.of("249.99", "PLN"));
    }

    @Test
    void shouldDeactivateProduct() {
        Product product = Product.create("Keyboard", Money.of("199.99", "PLN"));

        product.deactivate();

        assertThat(product.status()).isEqualTo(ProductStatus.INACTIVE);
    }
}
