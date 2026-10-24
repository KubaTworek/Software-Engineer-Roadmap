package pl.jakubtworek.backend.common.security;

import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.concurrent.*;
import tools.jackson.databind.ObjectMapper;

/** Client credentials: worker ma własną tożsamość, nie przechowuje tokenu klienta w sadze. */
public final class ServiceTokenProvider {
    private final URI endpoint;
    private final String body;
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofMillis(400)).build();
    private final ObjectMapper json=new ObjectMapper();
    private volatile String token;
    private volatile Instant validUntil=Instant.EPOCH;
    private CompletableFuture<String> inFlight;
    public ServiceTokenProvider(String endpoint,String id,String secret) {
        if (endpoint.isBlank() || id.isBlank() || secret.isBlank()) throw new IllegalArgumentException("Service identity configuration required");
        this.endpoint=URI.create(endpoint);
        body="grant_type=client_credentials&client_id="+encode(id)+"&client_secret="+encode(secret);
    }
    public synchronized CompletableFuture<String> token() {
        if (Instant.now().isBefore(validUntil)) return CompletableFuture.completedFuture(token);
        if (inFlight != null) return inFlight;
        var request=HttpRequest.newBuilder(endpoint).timeout(Duration.ofMillis(600))
                .header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(body)).build();
        inFlight=client.sendAsync(request,HttpResponse.BodyHandlers.ofString()).thenApply(response -> {
            if (response.statusCode()!=200) throw new IllegalStateException("Service identity unavailable");
            var value=json.readTree(response.body());
            String access=value.path("access_token").asString();
            long ttl=value.path("expires_in").asLong();
            if (access.isBlank() || ttl < 1) throw new IllegalStateException("Invalid service token response");
            token=access; validUntil=Instant.now().plusSeconds(Math.max(0,Math.min(ttl,3600)-10));
            return access;
        });
        var result=inFlight;
        result.whenComplete((value,failure) -> { synchronized(this) { if(inFlight==result) inFlight=null; } });
        return result;
    }
    private static String encode(String value) { return URLEncoder.encode(value,StandardCharsets.UTF_8); }
}
