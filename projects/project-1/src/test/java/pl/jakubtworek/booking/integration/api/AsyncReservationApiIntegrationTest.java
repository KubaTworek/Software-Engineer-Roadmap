package pl.jakubtworek.booking.integration.api;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import pl.jakubtworek.booking.dto.EventCreateRequest;
import pl.jakubtworek.booking.dto.ReservationCreateRequest;
import pl.jakubtworek.booking.entity.Organization;
import pl.jakubtworek.booking.repository.OrganizationRepository;

import java.time.OffsetDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pl.jakubtworek.booking.support.SecurityTestActors.customer;
import static pl.jakubtworek.booking.support.SecurityTestActors.eventManager;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql(statements = {
        "DELETE FROM outbound_messages",
        "DELETE FROM audit_logs",
        "DELETE FROM reservations",
        "DELETE FROM capacity_pools",
        "DELETE FROM refresh_tokens",
        "DELETE FROM app_users",
        "DELETE FROM events",
        "DELETE FROM customers",
        "DELETE FROM organizations"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class AsyncReservationApiIntegrationTest {
    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    OrganizationRepository organizationRepository;

    @Test
    void confirmAsyncEndpointConfirmsReservationWhenPaymentIsApproved() throws Exception {
        // given
        String reservationId = createReservation("api-async-approved@example.com");

        // when & then
        mockMvc.perform(post("/api/reservations/{reservationId}/confirm-async", reservationId)
                        .with(customer("api-async-approved@example.com"))
                        .queryParam("paymentScenario", "APPROVED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void confirmAsyncEndpointReturnsBusinessErrorWhenPaymentIsDeclined() throws Exception {
        // given
        String reservationId = createReservation("api-async-declined@example.com");

        // when & then
        mockMvc.perform(post("/api/reservations/{reservationId}/confirm-async", reservationId)
                        .with(customer("api-async-declined@example.com"))
                        .queryParam("paymentScenario", "DECLINED"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
    }

    private String createReservation(String email) throws Exception {
        Organization organization = organizationRepository.save(new Organization("Async Org " + email));
        String eventJson = mockMvc.perform(post("/api/events")
                        .with(eventManager(organization.getId()))
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
        String eventId = objectMapper.readTree(eventJson).get("id").stringValue();

        String reservationJson = mockMvc.perform(post("/api/events/{eventId}/reservations", eventId)
                        .with(customer(email))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReservationCreateRequest("API Async User", email))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(reservationJson).get("id").stringValue();
    }
}
