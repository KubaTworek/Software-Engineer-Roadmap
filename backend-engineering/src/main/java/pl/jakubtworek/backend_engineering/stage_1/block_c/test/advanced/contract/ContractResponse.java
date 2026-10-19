package pl.jakubtworek.backend_engineering.stage_1.block_c.test.advanced.contract;

import java.util.Map;

public record ContractResponse(int status, Map<String, Object> body) {

    public ContractResponse {
        body = Map.copyOf(body);
    }
}
