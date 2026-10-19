package pl.jakubtworek.backend_engineering.stage_1.block_a.virtual_threads;

public record RequestContext(String requestId, String principal) {

    public RequestContext {
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("requestId is required");
        }
        if (principal == null || principal.isBlank()) {
            throw new IllegalArgumentException("principal is required");
        }
    }
}
