package com.example.paymentsystem.payment.api;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldCreatePaymentAndReturnItById() throws Exception {
        String request = """
                {
                  "orderId": "ord_123",
                  "customerId": "cus_123",
                  "amount": 10000,
                  "currency": "PLN"
                }
                """;

        String responseBody = mockMvc.perform(post("/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentId").exists())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.checkoutUrl").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode json = objectMapper.readTree(responseBody);
        String paymentId = json.get("paymentId").asText();

        mockMvc.perform(get("/v1/payments/{paymentId}", paymentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value(paymentId))
                .andExpect(jsonPath("$.orderId").value("ord_123"))
                .andExpect(jsonPath("$.amount").value(10000));

        assertThat(paymentId).isNotBlank();
    }

    @Test
    void shouldReturnBadRequestForInvalidAmount() throws Exception {
        String request = """
                {
                  "orderId": "ord_123",
                  "amount": 0,
                  "currency": "PLN"
                }
                """;

        mockMvc.perform(post("/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.amount").exists());
    }
}
