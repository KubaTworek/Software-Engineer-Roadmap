package pl.jakubtworek.backend_engineering.stage_3.block_b.structured_logs;

public final class CheckoutObservabilityExample {

    public static void main(String[] args) {
        ServiceResource resource = ServiceResource.builder()
                .serviceName("checkout-api")
                .serviceVersion("1.42.0")
                .deploymentEnvironmentName("production")
                .serviceInstanceId("8b9da0f8-1f55-4a63-9ae8-d3e9f8953b9d")
                .build();

        CorrelationContext correlationContext = new CorrelationContext(
                "req-01JV3D9Q4PSZ3BXKZ3WN9Q5D1K",
                "4bf92f3577b34da6a3ce929d0e0e4736",
                "f03067aa0ba902b7"
        );

        HttpLogEvents httpLogEvents = new HttpLogEvents(resource);
        CacheLogEvents cacheLogEvents = new CacheLogEvents(resource);

        StructuredLogEvent httpEvent = httpLogEvents.requestCompleted(
                correlationContext,
                "POST",
                "/orders/:id/pay",
                200,
                183
        );

        StructuredLogEvent redisEvent = cacheLogEvents.redisLookupCompleted(
                correlationContext,
                "GET",
                "redis.prod.svc",
                false,
                6
        );

        System.out.println(httpEvent.toMap());
        System.out.println(redisEvent.toMap());
    }
}