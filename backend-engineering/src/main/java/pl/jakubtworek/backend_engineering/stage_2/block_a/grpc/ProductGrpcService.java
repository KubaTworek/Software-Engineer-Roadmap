package pl.jakubtworek.backend_engineering.stage_2.block_a.grpc;

import io.grpc.Context;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import pl.jakubtworek.backend_engineering.stage_2.block_a.grpc.runtime.ProductQueryGrpc;
import pl.jakubtworek.backend_engineering.stage_2.block_a.grpc.runtime.ProductReply;
import pl.jakubtworek.backend_engineering.stage_2.block_a.grpc.runtime.ProductRequest;
import pl.jakubtworek.backend_engineering.stage_2.block_a.reference_flow.ProductQueryUseCase;
import pl.jakubtworek.backend_engineering.stage_2.block_a.reference_flow.ProductSnapshot;

import java.time.Duration;

/** Generated-stub adapter over the same application port used by GraphQL. */
public final class ProductGrpcService extends ProductQueryGrpc.ProductQueryImplBase {
    private final ProductQueryUseCase productQuery;
    private final Duration artificialLatency;

    public ProductGrpcService(ProductQueryUseCase productQuery) {
        this(productQuery, Duration.ZERO);
    }

    public ProductGrpcService(ProductQueryUseCase productQuery, Duration artificialLatency) {
        this.productQuery = productQuery;
        this.artificialLatency = artificialLatency;
    }

    @Override
    public void getProduct(ProductRequest request, StreamObserver<ProductReply> responseObserver) {
        if (request.getProductId().isBlank()) {
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription("product_id is required").asRuntimeException());
            return;
        }
        if (!waitWhileCallIsAlive()) {
            return;
        }
        try {
            ProductSnapshot product = productQuery.find(request.getProductId());
            String traceparent = TraceparentServerInterceptor.CONTEXT_KEY.get();
            responseObserver.onNext(ProductReply.newBuilder()
                    .setProductId(product.id())
                    .setName(product.name())
                    .setVersion(product.version())
                    .setTraceparent(traceparent == null ? "" : traceparent)
                    .build());
            responseObserver.onCompleted();
        } catch (IllegalArgumentException exception) {
            responseObserver.onError(Status.NOT_FOUND.withDescription(exception.getMessage()).asRuntimeException());
        }
    }

    private boolean waitWhileCallIsAlive() {
        long remainingMillis = artificialLatency.toMillis();
        while (remainingMillis > 0) {
            if (Context.current().isCancelled()) {
                return false;
            }
            long sleep = Math.min(5, remainingMillis);
            try {
                Thread.sleep(sleep);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return false;
            }
            remainingMillis -= sleep;
        }
        return !Context.current().isCancelled();
    }
}
