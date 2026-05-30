package pl.jakubtworek.booking.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import pl.jakubtworek.booking.dto.EventCreateRequest;
import pl.jakubtworek.booking.dto.ReservationCreateRequest;

import java.time.OffsetDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql(statements = {
        "DELETE FROM outbound_messages",
        "DELETE FROM audit_logs",
        "DELETE FROM reservations",
        "DELETE FROM capacity_pools",
        "DELETE FROM app_users",
        "DELETE FROM events",
        "DELETE FROM customers",
        "DELETE FROM organizations"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class ApiAsyncStage3IntegrationTest {
    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void confirmAsyncEndpointConfirmsReservationWhenPaymentIsApproved() throws Exception {
        String reservationId = createReservation("api-async-approved@example.com");

        mockMvc.perform(post("/api/reservations/{reservationId}/confirm-async", reservationId)
                        .queryParam("paymentScenario", "APPROVED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void confirmAsyncEndpointReturnsBusinessErrorWhenPaymentIsDeclined() throws Exception {
        String reservationId = createReservation("api-async-declined@example.com");

        mockMvc.perform(post("/api/reservations/{reservationId}/confirm-async", reservationId)
                        .queryParam("paymentScenario", "DECLINED"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
    }

    private String createReservation(String email) throws Exception {
        String eventJson = mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EventCreateRequest(
                                "HTTP Async Workshop",
                                "Warsaw",
                                "education",
                                OffsetDateTime.now().plusDays(20),
                                5
                        ))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String eventId = objectMapper.readTree(eventJson).get("id").asText();

        String reservationJson = mockMvc.perform(post("/api/events/{eventId}/reservations", eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReservationCreateRequest("API Async User", email))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(reservationJson).get("id").asText();
    }
}
