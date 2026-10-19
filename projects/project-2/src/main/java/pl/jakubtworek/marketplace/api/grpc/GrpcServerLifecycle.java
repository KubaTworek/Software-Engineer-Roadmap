package pl.jakubtworek.marketplace.api.grpc;

import io.grpc.Server;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import java.io.IOException;
import io.grpc.ServerInterceptors;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.netty.handler.ssl.ClientAuth;
import java.io.File;

/** Osobny transport uruchamiany jawnie przez marketplace.grpc.enabled=true. */
@Component
@ConditionalOnProperty(name = "marketplace.grpc.enabled", havingValue = "true")
public class GrpcServerLifecycle implements SmartLifecycle {
    private final OrderingGrpcService service;
    private final int port;
    private final GrpcJwtAuthenticationInterceptor authentication;
    private final String certificate;
    private final String privateKey;
    private final String clientCa;
    private volatile Server server;
    public GrpcServerLifecycle(OrderingGrpcService service, GrpcJwtAuthenticationInterceptor authentication,
            @Value("${marketplace.grpc.port:9090}") int port,
            @Value("${marketplace.grpc.tls.certificate:}") String certificate,
            @Value("${marketplace.grpc.tls.private-key:}") String privateKey,
            @Value("${marketplace.grpc.tls.client-ca:}") String clientCa) {
        this.service = service; this.authentication = authentication; this.port = port;
        this.certificate = certificate; this.privateKey = privateKey; this.clientCa = clientCa;
    }
    @Override public void start() {
        try {
            var builder = NettyServerBuilder.forPort(port)
                    .addService(ServerInterceptors.intercept(service, authentication));
            if (!certificate.isBlank() || !privateKey.isBlank() || !clientCa.isBlank()) {
                if (certificate.isBlank() || privateKey.isBlank() || clientCa.isBlank()) {
                    throw new IllegalStateException("gRPC mTLS requires certificate, private-key and client-ca");
                }
                builder.sslContext(GrpcSslContexts.forServer(new File(certificate), new File(privateKey))
                        .trustManager(new File(clientCa)).clientAuth(ClientAuth.REQUIRE).build());
            }
            server = builder.build().start();
        }
        catch (IOException exception) { throw new IllegalStateException("Cannot start gRPC server", exception); }
    }
    @Override @PreDestroy public void stop() { if (server != null) server.shutdown(); }
    @Override public boolean isRunning() { return server != null && !server.isShutdown(); }
}
