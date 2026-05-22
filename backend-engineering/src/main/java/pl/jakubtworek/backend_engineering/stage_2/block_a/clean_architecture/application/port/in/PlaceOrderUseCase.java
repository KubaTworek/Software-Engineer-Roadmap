package pl.jakubtworek.backend_engineering.stage_2.block_a.clean_architecture.application.port.in;

// Input port.
// It defines what the application can do from the outside world perspective.
public interface PlaceOrderUseCase {

    PlaceOrderResult placeOrder(PlaceOrderCommand command);
}