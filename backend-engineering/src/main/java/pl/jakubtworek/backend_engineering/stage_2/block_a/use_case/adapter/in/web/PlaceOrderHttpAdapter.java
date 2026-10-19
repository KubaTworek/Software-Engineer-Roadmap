package pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.adapter.in.web;

import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.application.command.PlaceOrderCommand;
import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.application.command.PlaceOrderLineCommand;
import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.application.port.PlaceOrderUseCase;

import java.util.Objects;

/** Maps an HTTP-specific contract to the inbound application port. */
public final class PlaceOrderHttpAdapter {

    private final PlaceOrderUseCase useCase;

    public PlaceOrderHttpAdapter(PlaceOrderUseCase useCase) {
        this.useCase = Objects.requireNonNull(useCase, "useCase must not be null");
    }

    public PlaceOrderHttpResponse placeOrder(PlaceOrderHttpRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        PlaceOrderCommand command = new PlaceOrderCommand(
                request.customerId(),
                request.currency(),
                request.lines().stream()
                        .map(line -> new PlaceOrderLineCommand(
                                line.productId(),
                                line.quantity(),
                                line.unitPrice()
                        ))
                        .toList(),
                request.expectedTotal()
        );

        return new PlaceOrderHttpResponse(useCase.placeOrder(command).value());
    }
}
