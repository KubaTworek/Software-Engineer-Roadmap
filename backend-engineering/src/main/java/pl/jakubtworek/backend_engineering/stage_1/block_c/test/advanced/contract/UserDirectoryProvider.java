package pl.jakubtworek.backend_engineering.stage_1.block_c.test.advanced.contract;

import java.util.Map;

/** Provider side of the small consumer-driven contract laboratory. */
public final class UserDirectoryProvider {

    public ContractResponse handle(String method, String path) {
        if ("GET".equals(method) && "/internal/users/42".equals(path)) {
            return new ContractResponse(200, Map.of(
                    "id", 42,
                    "displayName", "Ada",
                    "active", true
            ));
        }
        return new ContractResponse(404, Map.of("code", "USER_NOT_FOUND"));
    }
}
