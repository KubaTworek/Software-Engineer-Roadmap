package com.example.paymentsystem.payment.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.paymentsystem.merchant.Merchant;
import com.example.paymentsystem.merchant.MerchantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never",
        "spring.flyway.enabled=false"
})
@AutoConfigureMockMvc
@WithMockUser(roles = {"ADMIN", "OPERATOR"})
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MerchantRepository merchantRepository;

    private String merchantId;

    @BeforeEach
    void createMerchantBoundary() {
        // Payment nie może istnieć bez merchanta; test przygotowuje jawnie tę granicę domenową.
        merchantId = merchantRepository.save(new Merchant("API test merchant", "PLN"))
                .getMerchantId()
                .toString();
    }

    @Test
    void shouldCreatePaymentAndReturnItById() throws Exception {
        String request = """
                {
                  "merchantId": "%s",
                  "orderId": "ord_123",
                  "customerId": "cus_123",
                  "amount": 10000,
                  "currency": "PLN"
                }
                """.formatted(merchantId);

        String responseBody = mockMvc.perform(post("/v1/payments")
                        // Każda komenda płatnicza musi być bezpieczna przy ponowieniu żądania.
                        .header("Idempotency-Key", "create-payment-ord-123")
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
                  "merchantId": "%s",
                  "orderId": "ord_123",
                  "amount": 0,
                  "currency": "PLN"
                }
                """.formatted(merchantId);

        mockMvc.perform(post("/v1/payments")
                        .header("Idempotency-Key", "invalid-payment-ord-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.amount").exists());
    }

    @Test
    @WithAnonymousUser
    void shouldRejectAnonymousAdministrativeMutation() throws Exception {
        mockMvc.perform(post("/v1/admin/providers/PAYU_MOCK/health")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"OPEN\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void merchantCannotReadAnotherMerchantsPayment() throws Exception {
        String request = """
                {"merchantId":"%s","orderId":"isolated","amount":10000,"currency":"PLN"}
                """.formatted(merchantId);
        String body = mockMvc.perform(post("/v1/payments")
                        .header("Idempotency-Key", "tenant-owner")
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String paymentId = objectMapper.readTree(body).get("paymentId").asText();

        mockMvc.perform(get("/v1/payments/{paymentId}", paymentId)
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                                .user("merchant:" + java.util.UUID.randomUUID()).roles("MERCHANT")))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldReplayUnknownOutcomeWithoutCallingAnotherProvider() throws Exception {
        String request = """
                {"merchantId":"%s","orderId":"ambiguous","amount":10000,"currency":"PLN"}
                """.formatted(merchantId);

        String first = mockMvc.perform(post("/v1/payments")
                        .header("Idempotency-Key", "simulate-timeout-api")
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("UNKNOWN"))
                .andReturn().getResponse().getContentAsString();

        String replay = mockMvc.perform(post("/v1/payments")
                        .header("Idempotency-Key", "simulate-timeout-api")
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isServiceUnavailable())
                .andReturn().getResponse().getContentAsString();

        assertThat(replay).isEqualTo(first);
    }
}
