package pl.jakubtworek.backend_engineering.stage_1.block_c.test.advanced.contract;

import java.util.Map;
import java.util.Objects;

/** Consumer that depends only on the fields declared in its contract. */
public final class UserDirectoryClient {

    private final Transport transport;

    public UserDirectoryClient(Transport transport) {
        this.transport = Objects.requireNonNull(transport, "transport");
    }

    public UserProfile findRequired(int userId) {
        ContractResponse response = transport.get("/internal/users/" + userId);
        if (response.status() != 200) {
            throw new IllegalStateException("user lookup failed with status " + response.status());
        }

        Map<String, Object> body = response.body();
        return new UserProfile(
                ((Number) body.get("id")).intValue(),
                (String) body.get("displayName"),
                (Boolean) body.get("active")
        );
    }

    @FunctionalInterface
    public interface Transport {
        ContractResponse get(String path);
    }

    public record UserProfile(int id, String displayName, boolean active) {
    }
}
