package pl.jakubtworek.backend_engineering.stage_2.block_a.api_design;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ApiContractCompatibilityTest {

    private final ApiContractCompatibilityChecker checker = new ApiContractCompatibilityChecker();

    @Test
    void additiveOptionalFieldAndNewOperationAreBackwardCompatible() {
        ApiContractCompatibilityChecker.ApiContract previous = contract(operation(
                "POST", "/api/v1/orders", Set.of("customerEmail", "items"), Set.of("id", "status"), Set.of(201)));
        ApiContractCompatibilityChecker.ApiContract candidate = contract(
                operation("POST", "/api/v1/orders", Set.of("customerEmail", "items"),
                        Set.of("id", "status", "expedited"), Set.of(201)),
                operation("GET", "/api/v1/orders/{id}", Set.of(), Set.of("id", "status"), Set.of(200)));

        assertThat(checker.compare(previous, candidate).compatible()).isTrue();
    }

    @Test
    void removedOperationNewRequiredInputAndRemovedOutputAreBreaking() {
        ApiContractCompatibilityChecker.Operation create = operation(
                "POST", "/api/v1/orders", Set.of("customerEmail", "items"), Set.of("id", "status"), Set.of(201));
        ApiContractCompatibilityChecker.Operation get = operation(
                "GET", "/api/v1/orders/{id}", Set.of(), Set.of("id", "status"), Set.of(200));
        ApiContractCompatibilityChecker.ApiContract candidate = contract(operation(
                "POST", "/api/v1/orders", Set.of("customerEmail", "items", "currency"), Set.of("id"), Set.of(202)));

        ApiContractCompatibilityChecker.Result result = checker.compare(contract(create, get), candidate);

        assertThat(result.compatible()).isFalse();
        assertThat(result.breakingChanges()).containsExactlyInAnyOrder(
                "new required request field currency in POST /api/v1/orders",
                "removed success response 201 from POST /api/v1/orders",
                "removed response field status from POST /api/v1/orders",
                "removed operation GET /api/v1/orders/{id}"
        );
    }

    private static ApiContractCompatibilityChecker.ApiContract contract(
            ApiContractCompatibilityChecker.Operation... operations
    ) {
        return new ApiContractCompatibilityChecker.ApiContract(List.of(operations));
    }

    private static ApiContractCompatibilityChecker.Operation operation(
            String method,
            String path,
            Set<String> required,
            Set<String> response,
            Set<Integer> statuses
    ) {
        return new ApiContractCompatibilityChecker.Operation(
                new ApiContractCompatibilityChecker.OperationKey(method, path), required, response, statuses);
    }
}
