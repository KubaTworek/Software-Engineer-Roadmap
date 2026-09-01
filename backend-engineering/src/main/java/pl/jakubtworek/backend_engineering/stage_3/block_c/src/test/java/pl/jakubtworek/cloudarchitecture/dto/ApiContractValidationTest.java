package pl.jakubtworek.cloudarchitecture.dto;

import org.junit.jupiter.api.Test;
import pl.jakubtworek.cloudarchitecture.dto.CreateOrderRequest;
import pl.jakubtworek.cloudarchitecture.dto.OrderCreatedResponse;
import pl.jakubtworek.cloudarchitecture.dto.ProductDto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ApiContractValidationTest {

    @Test
    void createOrderDefensivelyCopiesProductIdentifiers() {
        List<Long> productIds = new ArrayList<>(List.of(10L));

        CreateOrderRequest request = new CreateOrderRequest("customer-1", productIds);
        productIds.add(11L);

        assertThat(request.productIds()).containsExactly(10L);
    }

    @Test
    void rejectsInvalidApiValues() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new CreateOrderRequest(" ", List.of(1L)));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new CreateOrderRequest("customer-1", List.of()));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ProductDto(1L, "Product", new BigDecimal("-0.01")));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new OrderCreatedResponse(0L, "ACCEPTED"));
    }
}
