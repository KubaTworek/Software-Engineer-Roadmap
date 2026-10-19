package com.example.ecommerce.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Minimalny test kompatybilności publicznego kontraktu HTTP i OpenAPI. */
@SpringBootTest(properties = {
        "app.inventory.expiration-worker.enabled=false",
        "app.catalog-cache.warmer-enabled=false"
})
@AutoConfigureMockMvc
class ApiContractTest {
    @Autowired MockMvc http;
    @Autowired ObjectMapper json;

    @Test
    void notFoundUsesProblemDetailsWithoutImplementationLeak() throws Exception {
        http.perform(get("/api/products/definitely-missing")
                        .header("X-Correlation-ID", "contract-test"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.correlationId").value("contract-test"))
                .andExpect(jsonPath("$.instance").value("/api/products/definitely-missing"));
    }

    @Test
    void generatedOpenApiKeepsCheckoutIdempotencyHeader() throws Exception {
        String document = http.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode checkout = json.readTree(document).at("/paths/~1api~1checkout/post");
        assertThat(checkout.isMissingNode()).as("POST /api/checkout remains public").isFalse();
        assertThat(checkout.path("parameters")).anySatisfy(parameter -> {
            assertThat(parameter.path("name").asText()).isEqualTo("Idempotency-Key");
            assertThat(parameter.path("in").asText()).isEqualTo("header");
            assertThat(parameter.path("required").asBoolean()).isTrue();
        });
    }
}
