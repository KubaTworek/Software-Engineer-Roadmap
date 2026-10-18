package pl.jakubtworek.marketplace.api.grpc;

import io.grpc.ManagedChannel;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import org.junit.jupiter.api.Test;
import pl.jakubtworek.marketplace.api.grpc.contract.GetOrderRequest;
import pl.jakubtworek.marketplace.api.grpc.contract.OrderingQueryServiceGrpc;
import pl.jakubtworek.marketplace.ordering.domain.CustomerId;
import pl.jakubtworek.marketplace.ordering.domain.Order;
import pl.jakubtworek.marketplace.ordering.domain.OrderLine;
import pl.jakubtworek.marketplace.ordering.infrastructure.InMemoryOrderRepository;
import pl.jakubtworek.marketplace.shared.kernel.Money;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class OrderingGrpcTransportTest {
    @Test void servesGeneratedProtobufContractOverRealGrpcTransport() throws Exception {
        var repository = new InMemoryOrderRepository();
        var order = Order.place(CustomerId.of(UUID.randomUUID()),
                List.of(new OrderLine(UUID.randomUUID(), 1, Money.of("10", "PLN"))), UUID.randomUUID());
        repository.save(order);
        String name = InProcessServerBuilder.generateName();
        var server = InProcessServerBuilder.forName(name).directExecutor()
                .addService(new OrderingGrpcService(repository)).build().start();
        ManagedChannel channel = InProcessChannelBuilder.forName(name).directExecutor().build();
        try {
            var response = OrderingQueryServiceGrpc.newBlockingStub(channel)
                    .getOrder(GetOrderRequest.newBuilder().setOrderId(order.id().value().toString()).build());
            assertThat(response.getOrderId()).isEqualTo(order.id().value().toString());
            assertThat(response.getStatus()).isEqualTo("PENDING");
        } finally { channel.shutdownNow(); server.shutdownNow(); }
    }
}
