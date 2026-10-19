package pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.application.port;

import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.application.command.PlaceOrderCommand;
import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.domain.model.OrderId;

/** Inbound port independent from HTTP, messaging and dependency injection. */
public interface PlaceOrderUseCase {

    OrderId placeOrder(PlaceOrderCommand command);
}
