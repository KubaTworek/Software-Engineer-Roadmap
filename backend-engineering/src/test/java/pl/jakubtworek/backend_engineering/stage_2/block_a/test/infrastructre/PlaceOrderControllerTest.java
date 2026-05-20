package pl.jakubtworek.backend_engineering.stage_2.block_a.test.infrastructre;

import org.junit.jupiter.api.Test;
import pl.jakubtworek.backend_engineering.stage_2.block_a.clean_architecture.adapter.in.web.PlaceOrderController;
import pl.jakubtworek.backend_engineering.stage_2.block_a.clean_architecture.adapter.in.web.PlaceOrderHttpRequest;
import pl.jakubtworek.backend_engineering.stage_2.block_a.clean_architecture.adapter.in.web.PlaceOrderHttpResponse;
import pl.jakubtworek.backend_engineering.stage_2.block_a.clean_architecture.application.port.in.PlaceOrderResult;
import pl.jakubtworek.backend_engineering.stage_2.block_a.clean_architecture.application.port.in.PlaceOrderUseCase;
import pl.jakubtworek.backend_engineering.stage_2.block_a.clean_architecture.application.port.in.PlaceOrderCommand;

import static org.junit.jupiter.api.Assertions.*;

// Test for the inbound web adapter.
// It verifies request-to-command and result-to-response mapping.
class PlaceOrderControllerTest {

    @Test
    void shouldMapHttpRequestToUseCaseCommandAndReturnResponse() {
        // Given
        FakePlaceOrderUseCase useCase = new FakePlaceOrderUseCase();

        PlaceOrderController controller = new PlaceOrderController(useCase);

        PlaceOrderHttpRequest request = new PlaceOrderHttpRequest("C-456");

        // When
        PlaceOrderHttpResponse response = controller.placeOrder(request);

        // Then
        assertEquals("C-456", useCase.lastCommand().customerId());
        assertEquals("O-123", response.orderId());
        assertEquals("PLACED", response.status());
    }

    // Fake use case used to test the web adapter without invoking the real application service.
    private static final class FakePlaceOrderUseCase implements PlaceOrderUseCase {

        private PlaceOrderCommand lastCommand;

        @Override
        public PlaceOrderResult placeOrder(PlaceOrderCommand command) {
            this.lastCommand = command;
            return new PlaceOrderResult("O-123", "PLACED");
        }

        public PlaceOrderCommand lastCommand() {
            return lastCommand;
        }
    }
}