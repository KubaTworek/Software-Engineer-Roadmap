package pl.jakubtworek.marketplace.api.grpc;

import io.grpc.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * W profilu secure ten sam JwtDecoder chroni HTTP i gRPC. Brak/niepoprawny Bearer
 * kończy wywołanie przed serwisem; w profilu lokalnym brak dekodera jawnie wyłącza
 * tę granicę. To uwierzytelnienie transportu nie zastępuje autoryzacji zasobu.
 */
@Component
public class GrpcJwtAuthenticationInterceptor implements ServerInterceptor {
    static final Context.Key<Jwt> JWT = Context.key("marketplace-jwt");
    private static final Metadata.Key<String> AUTHORIZATION =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);
    private final ObjectProvider<JwtDecoder> decoders;

    public GrpcJwtAuthenticationInterceptor(ObjectProvider<JwtDecoder> decoders) { this.decoders = decoders; }

    @Override public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
        JwtDecoder decoder = decoders.getIfAvailable();
        if (decoder == null) return next.startCall(call, headers);
        String value = headers.get(AUTHORIZATION);
        if (value == null || !value.startsWith("Bearer ")) {
            call.close(Status.UNAUTHENTICATED.withDescription("Bearer token required"), new Metadata());
            return new ServerCall.Listener<>() {};
        }
        try {
            Jwt jwt = decoder.decode(value.substring(7));
            return Contexts.interceptCall(Context.current().withValue(JWT, jwt), call, headers, next);
        } catch (RuntimeException invalidJwt) {
            call.close(Status.UNAUTHENTICATED.withDescription("invalid Bearer token"), new Metadata());
            return new ServerCall.Listener<>() {};
        }
    }
}
