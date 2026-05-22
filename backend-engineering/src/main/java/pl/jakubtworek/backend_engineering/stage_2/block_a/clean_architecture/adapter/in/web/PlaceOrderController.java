package pl.jakubtworek.backend_engineering.stage_2.block_a.clean_architecture.adapter.in.web;

import pl.jakubtworek.backend_engineering.stage_2.block_a.clean_architecture.application.port.in.PlaceOrderCommand;
import pl.jakubtworek.backend_engineering.stage_2.block_a.clean_architecture.application.port.in.PlaceOrderResult;
import pl.jakubtworek.backend_engineering.stage_2.block_a.clean_architecture.application.port.in.PlaceOrderUseCase;

// Inbound web adapter.
// In a real Spring application, this class may have @RestController,
// but the use case itself remains framework-independent.
public final class PlaceOrderController {

    private final PlaceOrderUseCase placeOrderUseCase;

    public PlaceOrderController(PlaceOrderUseCase placeOrderUseCase) {
        this.placeOrderUseCase = placeOrderUseCase;
    }

    // Converts HTTP DTO into application command.
    public PlaceOrderHttpResponse placeOrder(PlaceOrderHttpRequest request) {
        PlaceOrderResult result = placeOrderUseCase.placeOrder(
                new PlaceOrderCommand(request.customerId())
        );

        return new PlaceOrderHttpResponse(
                result.orderId(),
                result.status()
        );
    }
}