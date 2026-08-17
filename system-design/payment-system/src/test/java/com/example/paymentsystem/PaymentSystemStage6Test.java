package com.example.paymentsystem;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentSystemStage6Test {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void shouldCreateMarketplacePaymentRouteToPayuAndPayoutMerchant() throws Exception {
        String merchantId = createMerchant("Merchant PL", "PLN");

        String paymentJson = mockMvc.perform(post("/v1/payments")
                        .header("Idempotency-Key", "stage6-payment-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "merchantId": "%s",
                                  "orderId": "ord_1",
                                  "customerId": "cus_1",
                                  "amount": 10000,
                                  "currency": "PLN",
                                  "captureMode": "AUTOMATIC",
                                  "customerCountry": "PL",
                                  "ipCountry": "PL"
                                }
                                """.formatted(merchantId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.provider").value("PAYU_MOCK"))
                .andExpect(jsonPath("$.riskDecision").value("ALLOW"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String paymentId = objectMapper.readTree(paymentJson).get("paymentId").asText();

        mockMvc.perform(post("/v1/payments/{paymentId}/succeed", paymentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.platformFeeAmount").value(1000))
                .andExpect(jsonPath("$.merchantAmount").value(9000));

        mockMvc.perform(post("/v1/merchants/{merchantId}/payouts", merchantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currency\":\"PLN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(9000))
                .andExpect(jsonPath("$.status").value("PAID"));
    }

    @Test
    void shouldFallbackProviderWhenPreferredCircuitIsOpen() throws Exception {
        String merchantId = createMerchant("Merchant EUR", "EUR");

        mockMvc.perform(post("/v1/admin/providers/ADYEN_MOCK/health")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"OPEN\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/v1/payments")
                        .header("Idempotency-Key", "stage6-payment-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "merchantId": "%s",
                                  "orderId": "ord_2",
                                  "amount": 10000,
                                  "currency": "EUR",
                                  "captureMode": "AUTOMATIC"
                                }
                                """.formatted(merchantId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.provider").value("STRIPE_MOCK"));
    }

    @Test
    void shouldBlockHighRiskPayment() throws Exception {
        String merchantId = createMerchant("High Risk Merchant", "PLN");

        mockMvc.perform(post("/v1/payments")
                        .header("Idempotency-Key", "stage6-risk-block")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "merchantId": "%s",
                                  "orderId": "ord_risk",
                                  "amount": 700000,
                                  "currency": "USD",
                                  "customerCountry": "PL",
                                  "ipCountry": "RU"
                                }
                                """.formatted(merchantId)))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldOpenAndLoseChargeback() throws Exception {
        String merchantId = createMerchant("Chargeback Merchant", "PLN");

        String paymentJson = mockMvc.perform(post("/v1/payments")
                        .header("Idempotency-Key", "stage6-payment-3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "merchantId": "%s",
                                  "orderId": "ord_3",
                                  "amount": 10000,
                                  "currency": "PLN"
                                }
                                """.formatted(merchantId)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String paymentId = objectMapper.readTree(paymentJson).get("paymentId").asText();

        mockMvc.perform(post("/v1/payments/{paymentId}/succeed", paymentId))
                .andExpect(status().isOk());

        String cbJson = mockMvc.perform(post("/v1/payments/{paymentId}/chargebacks", paymentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":10000,\"reason\":\"fraudulent\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String chargebackId = objectMapper.readTree(cbJson).get("chargebackId").asText();

        mockMvc.perform(post("/v1/chargebacks/{chargebackId}/lose", chargebackId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LOST"));
    }

    private String createMerchant(String name, String currency) throws Exception {
        String json = mockMvc.perform(post("/v1/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "settlementCurrency": "%s"
                                }
                                """.formatted(name, currency)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(json).get("merchantId").asText();
    }
}
