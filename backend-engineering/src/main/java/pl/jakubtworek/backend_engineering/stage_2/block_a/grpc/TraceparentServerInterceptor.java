package pl.jakubtworek.backend_engineering.stage_2.block_a.grpc;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;

public final class TraceparentServerInterceptor implements ServerInterceptor {
    public static final Metadata.Key<String> HEADER = Metadata.Key.of("traceparent", Metadata.ASCII_STRING_MARSHALLER);
    public static final Context.Key<String> CONTEXT_KEY = Context.key("traceparent");

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
        String traceparent = headers.get(HEADER);
        Context context = Context.current().withValue(CONTEXT_KEY, traceparent == null ? "missing" : traceparent);
        return Contexts.interceptCall(context, call, headers, next);
    }
}
