package pl.jakubtworek.backend_engineering.stage_2.block_a.grpc;

import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.MetadataUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import pl.jakubtworek.backend_engineering.stage_2.block_a.grpc.runtime.ProductQueryGrpc;
import pl.jakubtworek.backend_engineering.stage_2.block_a.grpc.runtime.ProductReply;
import pl.jakubtworek.backend_engineering.stage_2.block_a.grpc.runtime.ProductRequest;
import pl.jakubtworek.backend_engineering.stage_2.block_a.reference_flow.InMemoryProductQueryUseCase;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GrpcInProcessRuntimeTest {
    private Server server;
    private ManagedChannel channel;

    @AfterEach
    void closeRuntime() throws InterruptedException {
        if (channel != null) channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        if (server != null) server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test
    void generatedStubCallsRealServerAndPropagatesTraceMetadata() throws Exception {
        start(new ProductGrpcService(new InMemoryProductQueryUseCase()));
        Metadata metadata = new Metadata();
        metadata.put(TraceparentServerInterceptor.HEADER, "00-abc-def-01");

        ProductReply reply = ProductQueryGrpc.newBlockingStub(channel)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata))
                .getProduct(ProductRequest.newBuilder().setProductId("p-1").build());

        assertThat(reply.getName()).isEqualTo("Java Backend Handbook");
        assertThat(reply.getVersion()).isEqualTo(3);
        assertThat(reply.getTraceparent()).isEqualTo("00-abc-def-01");
    }

    @Test
    void clientDeadlineCancelsSlowServerWork() throws Exception {
        start(new ProductGrpcService(new InMemoryProductQueryUseCase(), Duration.ofMillis(200)));
        ProductQueryGrpc.ProductQueryBlockingStub stub = ProductQueryGrpc.newBlockingStub(channel)
                .withDeadlineAfter(20, TimeUnit.MILLISECONDS);

        assertThatThrownBy(() -> stub.getProduct(ProductRequest.newBuilder().setProductId("p-1").build()))
                .isInstanceOfSatisfying(StatusRuntimeException.class,
                        exception -> assertThat(exception.getStatus().getCode()).isEqualTo(Status.Code.DEADLINE_EXCEEDED));
    }

    private void start(ProductGrpcService service) throws Exception {
        String name = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(name)
                .directExecutor()
                .addService(io.grpc.ServerInterceptors.intercept(service, new TraceparentServerInterceptor()))
                .build().start();
        channel = InProcessChannelBuilder.forName(name).directExecutor().build();
    }
}
