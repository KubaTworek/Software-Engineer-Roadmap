package pl.jakubtworek.backend_engineering.stage_2.block_a.api_design;

/**
 * Błąd na granicy API. Kod HTTP opisuje semantykę protokołu, a stabilny
 * {@code code} pozwala klientowi reagować bez parsowania tekstu komunikatu.
 */
public final class ApiFailure extends RuntimeException {

    private final int status;
    private final String code;

    private ApiFailure(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static ApiFailure notFound(String resource) {
        return new ApiFailure(404, "resource_not_found", resource + " does not exist");
    }

    public static ApiFailure conflict(String message) {
        return new ApiFailure(409, "idempotency_conflict", message);
    }

    public static ApiFailure preconditionRequired() {
        return new ApiFailure(428, "precondition_required", "If-Match header is required");
    }

    public static ApiFailure preconditionFailed(String expected, String actual) {
        return new ApiFailure(412, "stale_resource", "Expected " + expected + " but received " + actual);
    }

    public static ApiFailure domainRule(String message) {
        return new ApiFailure(422, "domain_rule_violated", message);
    }

    public static ApiFailure badRequest(String message) {
        return new ApiFailure(400, "invalid_request", message);
    }

    public int status() {
        return status;
    }

    public String code() {
        return code;
    }
}
