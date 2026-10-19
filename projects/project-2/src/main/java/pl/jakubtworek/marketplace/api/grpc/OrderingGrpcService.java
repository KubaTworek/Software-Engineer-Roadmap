package pl.jakubtworek.marketplace.api.grpc;

import io.grpc.Context;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Component;
import pl.jakubtworek.marketplace.api.grpc.contract.GetOrderRequest;
import pl.jakubtworek.marketplace.api.grpc.contract.GetOrderResponse;
import pl.jakubtworek.marketplace.api.grpc.contract.OrderingQueryServiceGrpc;
import pl.jakubtworek.marketplace.ordering.application.OrderRepository;
import pl.jakubtworek.marketplace.ordering.domain.OrderId;
import java.util.UUID;

/**
 * Adapter gRPC wygenerowany z kontraktu protobuf. Deadline transportu jest
 * sprawdzany przed odczytem; produkcyjny adapter przekazałby pozostały budżet do
 * kolejnych klientów zamiast rozpoczynać nowy timeout na każdym hopie.
 */
@Component
public class OrderingGrpcService extends OrderingQueryServiceGrpc.OrderingQueryServiceImplBase {
    private final OrderRepository orders;
    public OrderingGrpcService(OrderRepository orders) { this.orders = orders; }

    @Override
    public void getOrder(GetOrderRequest request, StreamObserver<GetOrderResponse> observer) {
        if (Context.current().isCancelled()) {
            observer.onError(Status.CANCELLED.withDescription("caller cancelled request").asRuntimeException());
            return;
        }
        if (Context.current().getDeadline() != null && Context.current().getDeadline().isExpired()) {
            observer.onError(Status.DEADLINE_EXCEEDED.asRuntimeException());
            return;
        }
        try {
            var order = orders.findById(OrderId.of(UUID.fromString(request.getOrderId())))
                    .orElseThrow(() -> Status.NOT_FOUND.asRuntimeException());
            var jwt = GrpcJwtAuthenticationInterceptor.JWT.get();
            if (jwt != null) {
                String scope = jwt.getClaimAsString("scope");
                boolean admin = scope != null && java.util.Arrays.asList(scope.split(" ")).contains("admin");
                String customer = jwt.getClaimAsString("customer_id");
                if (!admin && !order.customerId().value().toString().equals(customer)) {
                    throw Status.PERMISSION_DENIED.withDescription("caller does not own order").asRuntimeException();
                }
            }
            observer.onNext(GetOrderResponse.newBuilder()
                    .setOrderId(order.id().value().toString())
                    .setStatus(order.status().name())
                    .setVersion(order.version()).build());
            observer.onCompleted();
        } catch (IllegalArgumentException invalidId) {
            observer.onError(Status.INVALID_ARGUMENT.withDescription("order_id must be UUID").asRuntimeException());
        } catch (io.grpc.StatusRuntimeException expected) {
            observer.onError(expected);
        }
    }
}
