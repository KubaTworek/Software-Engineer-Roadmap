package pl.jakubtworek.backend_engineering.stage_2.block_a.clean_architecture.config;

import pl.jakubtworek.backend_engineering.stage_2.block_a.clean_architecture.adapter.in.web.PlaceOrderController;
import pl.jakubtworek.backend_engineering.stage_2.block_a.clean_architecture.adapter.out.messaging.KafkaClient;
import pl.jakubtworek.backend_engineering.stage_2.block_a.clean_architecture.adapter.out.messaging.KafkaOrderEventPublisherAdapter;
import pl.jakubtworek.backend_engineering.stage_2.block_a.clean_architecture.adapter.out.messaging.OrderEventMessageMapper;
import pl.jakubtworek.backend_engineering.stage_2.block_a.clean_architecture.adapter.out.persistance.JpaOrderRepositoryAdapter;
import pl.jakubtworek.backend_engineering.stage_2.block_a.clean_architecture.adapter.out.persistance.OrderPersistenceMapper;
import pl.jakubtworek.backend_engineering.stage_2.block_a.clean_architecture.adapter.out.persistance.SpringDataOrderRepository;
import pl.jakubtworek.backend_engineering.stage_2.block_a.clean_architecture.application.port.in.PlaceOrderUseCase;
import pl.jakubtworek.backend_engineering.stage_2.block_a.clean_architecture.application.port.out.OrderEventPublisher;
import pl.jakubtworek.backend_engineering.stage_2.block_a.clean_architecture.application.port.out.OrderRepository;
import pl.jakubtworek.backend_engineering.stage_2.block_a.clean_architecture.application.service.PlaceOrderService;

// Composition root.
// This is the place where framework configuration wires ports and adapters together.
public final class SalesModuleConfiguration {

    public PlaceOrderController placeOrderController(
            SpringDataOrderRepository springDataRepository,
            KafkaClient kafkaClient
    ) {
        OrderRepository orderRepository = new JpaOrderRepositoryAdapter(
                springDataRepository,
                new OrderPersistenceMapper()
        );

        OrderEventPublisher eventPublisher = new KafkaOrderEventPublisherAdapter(
                kafkaClient,
                new OrderEventMessageMapper()
        );

        PlaceOrderUseCase useCase = new PlaceOrderService(
                orderRepository,
                eventPublisher
        );

        return new PlaceOrderController(useCase);
    }
}