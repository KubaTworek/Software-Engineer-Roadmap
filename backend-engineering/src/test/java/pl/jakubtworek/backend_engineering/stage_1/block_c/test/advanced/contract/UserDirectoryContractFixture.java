package pl.jakubtworek.backend_engineering.stage_1.block_c.test.advanced.contract;

import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

record UserDirectoryContractFixture(
        String consumer,
        String provider,
        String description,
        Request request,
        Response response
) {

    static UserDirectoryContractFixture load() {
        try (InputStream stream = UserDirectoryContractFixture.class.getResourceAsStream(
                "/contracts/user-directory-v1.json")) {
            if (stream == null) {
                throw new IllegalStateException("consumer contract fixture is missing");
            }
            return new ObjectMapper().readValue(stream, UserDirectoryContractFixture.class);
        } catch (IOException exception) {
            throw new IllegalStateException("cannot read consumer contract fixture", exception);
        }
    }

    record Request(String method, String path) {
    }

    record Response(int status, Map<String, Object> body) {
    }
}
