package pl.jakubtworek.backend_engineering.stage_2.block_a.api_design;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class OpenApiContractTest {

    @Test
    void publishedContractContainsEveryExecutableCapabilityAndFailureMode() throws IOException {
        String contract = readContract();

        assertThat(contract)
                .contains("openapi: 3.1.0")
                .contains("operationId: createOrder")
                .contains("operationId: listOrders")
                .contains("operationId: getOrder")
                .contains("operationId: replaceOrder")
                .contains("operationId: patchOrder")
                .contains("operationId: deleteOrder")
                .contains("operationId: startOrderCancellation")
                .contains("operationId: getOperation")
                .contains("Idempotency-Key")
                .contains("If-Match")
                .contains("application/merge-patch+json")
                .contains("application/problem+json")
                .contains("webhooks:")
                .contains("Webhook-Signature")
                .contains("'202'")
                .contains("'412'")
                .contains("'422'")
                .contains("'428'");
    }

    @Test
    void clientControlledCreateFieldsAreExplicitAndUnknownFieldsAreRejectedByTheContract() throws IOException {
        String contract = readContract();
        int createSchema = contract.indexOf("    CreateOrderRequest:");
        int replaceSchema = contract.indexOf("    ReplaceOrderRequest:");
        String createSection = contract.substring(createSchema, replaceSchema);

        assertThat(createSection)
                .contains("additionalProperties: false")
                .contains("required: [customerEmail, items, expedited]")
                .doesNotContain("status:")
                .doesNotContain("version:")
                .doesNotContain("createdAt:");
    }

    private static String readContract() throws IOException {
        try (InputStream stream = OpenApiContractTest.class.getResourceAsStream("/openapi/order-api-v1.yaml")) {
            assertThat(stream).as("OpenAPI resource").isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
