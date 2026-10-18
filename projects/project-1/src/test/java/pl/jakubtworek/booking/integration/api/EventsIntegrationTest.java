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

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
        "DELETE FROM app_users",
        "DELETE FROM events",
        "DELETE FROM customers",
        "DELETE FROM organizations"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class EventsIntegrationTest {
    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    OrganizationRepository organizationRepository;

    @Test
    void supportsFullHttpMvpFlow() throws Exception {
        Organization organization = organizationRepository.save(new Organization("HTTP MVP Org"));
        String eventBody = objectMapper.writeValueAsString(new EventCreateRequest(
                "HTTP MVP Workshop",
                "Warsaw",
                "education",
                OffsetDateTime.now().plusDays(45),
                2
        ));

        String eventJson = mockMvc.perform(post("/api/events")
                        .with(eventManager(organization.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.availableCapacity").value(2))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String eventId = objectMapper.readTree(eventJson).get("id").stringValue();

        String reservationBody = objectMapper.writeValueAsString(new ReservationCreateRequest(
                "Jan Kowalski",
                "jan.http@example.com"
        ));

        String reservationJson = mockMvc.perform(post("/api/events/{eventId}/reservations", eventId)
                        .with(customer("jan.http@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservationBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.eventId").value(eventId))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String reservationId = objectMapper.readTree(reservationJson).get("id").stringValue();

        mockMvc.perform(get("/api/events/{eventId}", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableCapacity").value(1));

        mockMvc.perform(post("/api/reservations/{reservationId}/confirm", reservationId)
                        .with(customer("jan.http@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.confirmedAt").isNotEmpty());

        mockMvc.perform(get("/api/reservations/{reservationId}", reservationId)
                        .with(customer("jan.http@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void returnsValidationErrorForInvalidEventRequest() throws Exception {
        Organization organization = organizationRepository.save(new Organization("Invalid Request Org"));
        // given
        String invalidBody = """
                {
                  "name": "",
                  "city": "",
                  "category": "education",
                  "startsAt": "2020-01-01T10:00:00+01:00",
                  "totalCapacity": 0
                }
                """;

        // when & then
        mockMvc.perform(post("/api/events")
                        .with(eventManager(organization.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fields", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    void hidesMissingOrForeignReservationBehindForbiddenResponse() throws Exception {
        // when & then
        mockMvc.perform(get("/api/reservations/{reservationId}", "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
                        .with(customer("outsider@example.com")))
                .andExpect(status().isForbidden());
    }

    @Test
    void distinguishesAuthenticationFromAuthorizationAndProtectsTenantBoundary() throws Exception {
        Organization owner = organizationRepository.save(new Organization("Security Owner Org"));
        Organization foreign = organizationRepository.save(new Organization("Security Foreign Org"));
        String eventBody = objectMapper.writeValueAsString(new EventCreateRequest(
                "Tenant Boundary Event", "Warsaw", "security",
                OffsetDateTime.now().plusDays(20), 2));

        // Brak tożsamości kończy się 401, zanim aplikacja zacznie oceniać
        // uprawnienia do organizacji lub wykonywać logikę biznesową.
        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventBody))
                .andExpect(status().isUnauthorized());

        String eventJson = mockMvc.perform(post("/api/events")
                        .with(eventManager(owner.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String eventId = objectMapper.readTree(eventJson).get("id").stringValue();

        // Manager jest poprawnie uwierzytelniony, lecz jego tenant nie jest
        // właścicielem eventu. Sam UUID zasobu nie może działać jak uprawnienie.
        mockMvc.perform(post("/api/events/{eventId}/reservations", eventId)
                        .with(eventManager(foreign.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ReservationCreateRequest("Foreign User", "foreign@example.test"))))
                .andExpect(status().isForbidden());

        String reservationJson = mockMvc.perform(post("/api/events/{eventId}/reservations", eventId)
                        .with(customer("owner@example.test"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ReservationCreateRequest("Owner User", "owner@example.test"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String reservationId = objectMapper.readTree(reservationJson).get("id").stringValue();

        mockMvc.perform(get("/api/reservations/{reservationId}", reservationId)
                        .with(customer("attacker@example.test")))
                .andExpect(status().isForbidden());
    }

    @Test
    void returnsConflictWhenCapacityIsUnavailable() throws Exception {
        Organization organization = organizationRepository.save(new Organization("Tiny Event Org"));
        // given
        String eventJson = mockMvc.perform(post("/api/events")
                        .with(eventManager(organization.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EventCreateRequest(
                                "Tiny Event",
                                "Warsaw",
                                "education",
                                OffsetDateTime.now().plusDays(10),
                                1
                        ))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String eventId = objectMapper.readTree(eventJson).get("id").stringValue();

        // when & then
        mockMvc.perform(post("/api/events/{eventId}/reservations", eventId)
                        .with(customer("first.http@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReservationCreateRequest("First User", "first.http@example.com"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/events/{eventId}/reservations", eventId)
                        .with(customer("second.http@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReservationCreateRequest("Second User", "second.http@example.com"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CAPACITY_UNAVAILABLE"));
    }
}
