package pl.jakubtworek.backend_engineering.stage_2.block_a.api_design;

import static org.hamcrest.Matchers.endsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class OrderApiHttpSemanticsTest {

    private static final UUID ORDER_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID OPERATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000201");
    private static final String CREATE_BODY = """
            {"customerEmail":"buyer@example.com","items":[{"sku":"BOOK-1","quantity":2}],"expedited":false}
            """;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-01-10T12:00:00Z"), ZoneOffset.UTC);
        Queue<UUID> orderIds = new ArrayDeque<>();
        orderIds.add(ORDER_ID);
        OrderService orders = new OrderService(clock, orderIds::remove);
        AsyncCancellationService cancellations = new AsyncCancellationService(
                orders, clock, () -> OPERATION_ID, ignored -> { });
        mvc = MockMvcBuilders
                .standaloneSetup(new OrderApiController(orders, cancellations))
                .setControllerAdvice(new ApiProblemHandler())
                .build();
    }

    @Test
    void postIsIdempotentForTheSameKeyAndPayloadButRejectsKeyReuse() throws Exception {
        mvc.perform(post("/api/v1/orders")
                        .header("Idempotency-Key", "request-101")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/v1/orders/" + ORDER_ID)))
                .andExpect(header().string("ETag", "\"v1\""))
                .andExpect(header().string("Idempotency-Replayed", "false"));

        mvc.perform(post("/api/v1/orders")
                        .header("Idempotency-Key", "request-101")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotency-Replayed", "true"))
                .andExpect(jsonPath("$.id").value(ORDER_ID.toString()));

        mvc.perform(post("/api/v1/orders")
                        .header("Idempotency-Key", "request-101")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY.replace("quantity\":2", "quantity\":3")))
                .andExpect(status().isConflict())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.code").value("idempotency_conflict"));
    }

    @Test
    void getSupportsConditionalRequests() throws Exception {
        createOrder();

        mvc.perform(get("/api/v1/orders/{id}", ORDER_ID))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"v1\""));

        mvc.perform(get("/api/v1/orders/{id}", ORDER_ID).header("If-None-Match", "\"v1\""))
                .andExpect(status().isNotModified())
                .andExpect(header().string("ETag", "\"v1\""));
    }

    @Test
    void putPatchAndDeleteRequireTheCurrentEntityTag() throws Exception {
        createOrder();
        String replacement = CREATE_BODY.replace("buyer@example.com", "new@example.com");

        mvc.perform(put("/api/v1/orders/{id}", ORDER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(replacement))
                .andExpect(result -> assertEquals(428, result.getResponse().getStatus()))
                .andExpect(jsonPath("$.code").value("precondition_required"));

        mvc.perform(put("/api/v1/orders/{id}", ORDER_ID)
                        .header("If-Match", "\"v1\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(replacement))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"v2\""));

        mvc.perform(patch("/api/v1/orders/{id}", ORDER_ID)
                        .header("If-Match", "\"v1\"")
                        .contentType("application/merge-patch+json")
                        .content("{\"expedited\":true}"))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.code").value("stale_resource"));

        mvc.perform(patch("/api/v1/orders/{id}", ORDER_ID)
                        .header("If-Match", "\"v2\"")
                        .contentType("application/merge-patch+json")
                        .content("{\"expedited\":true}"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"v3\""))
                .andExpect(jsonPath("$.customerEmail").value("new@example.com"))
                .andExpect(jsonPath("$.expedited").value(true));

        mvc.perform(delete("/api/v1/orders/{id}", ORDER_ID).header("If-Match", "\"v3\""))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/orders/{id}", ORDER_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void validationErrorsAndDomainErrorsHaveDifferentStableCodes() throws Exception {
        mvc.perform(post("/api/v1/orders")
                        .header("Idempotency-Key", "invalid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY.replace("buyer@example.com", "not-an-email")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(jsonPath("$.violations[0].field").value("customerEmail"));

        String duplicateSku = """
                {"customerEmail":"buyer@example.com","items":[
                  {"sku":"BOOK-1","quantity":1},{"sku":"BOOK-1","quantity":2}
                ],"expedited":false}
                """;
        mvc.perform(post("/api/v1/orders")
                        .header("Idempotency-Key", "domain-error")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(duplicateSku))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("domain_rule_violated"));
    }

    @Test
    void malformedJsonAndUnknownContractFieldsAreRejectedAsClientErrors() throws Exception {
        mvc.perform(post("/api/v1/orders")
                        .header("Idempotency-Key", "malformed")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_request"));

        mvc.perform(post("/api/v1/orders")
                        .header("Idempotency-Key", "unknown-field")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY.replace("}", ",\"serverOwnedStatus\":\"CANCELLED\"}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_request"));
    }

    @Test
    void acceptedCancellationExposesASeparateOperationResource() throws Exception {
        createOrder();

        mvc.perform(post("/api/v1/orders/{id}/cancellations", ORDER_ID))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", endsWith("/api/v1/operations/" + OPERATION_ID)))
                .andExpect(jsonPath("$.state").value("PENDING"));

        mvc.perform(get("/api/v1/operations/{id}", OPERATION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(ORDER_ID.toString()))
                .andExpect(jsonPath("$.state").value("PENDING"));
    }

    private MvcResult createOrder() throws Exception {
        return mvc.perform(post("/api/v1/orders")
                        .header("Idempotency-Key", "setup-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isCreated())
                .andReturn();
    }
}
