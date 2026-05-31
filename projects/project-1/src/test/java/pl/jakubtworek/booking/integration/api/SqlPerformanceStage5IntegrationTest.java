package pl.jakubtworek.booking.integration.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import pl.jakubtworek.booking.entity.*;
import pl.jakubtworek.booking.repository.*;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static java.sql.Timestamp.from;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SqlPerformanceStage5IntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired EventRepository eventRepository;
    @Autowired CapacityPoolRepository capacityPoolRepository;
    @Autowired CustomerRepository customerRepository;
    @Autowired OrganizationRepository organizationRepository;
    @Autowired ReservationRepository reservationRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    private Organization organization;
    private Event event;
    private Customer customer;
    private Reservation newestReservation;
    private Reservation olderReservation;

    @BeforeEach
    void setUp() {
        cleanDatabase();

        organization = organizationRepository.save(new Organization("Stage 5 API Org"));
        customer = customerRepository.save(new Customer("Stage Five", "stage5-api@example.com"));
        event = eventRepository.save(new Event(
                organization,
                "Warsaw API Music",
                "Warsaw",
                "music",
                OffsetDateTime.of(2026, 6, 20, 20, 0, 0, 0, ZoneOffset.UTC)
        ));
        capacityPoolRepository.save(new CapacityPool(event, 50));

        olderReservation = reservationRepository.save(new Reservation(event, customer));
        newestReservation = new Reservation(event, customer);
        newestReservation.confirm();
        newestReservation = reservationRepository.save(newestReservation);
        setCreatedAt(olderReservation, "2026-06-01T10:00:00Z");
        setCreatedAt(newestReservation, "2026-06-02T10:00:00Z");
    }

    @Test
    void exposesEventSearchEndpoint() throws Exception {
        // when & then
        mockMvc.perform(get("/api/events")
                        .param("city", "Warsaw")
                        .param("from", "2026-06-01T00:00:00Z")
                        .param("category", "music")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(event.getId().toString()))
                .andExpect(jsonPath("$[0].city").value("Warsaw"));
    }

    @Test
    void exposesOrganizationReservationEndpoint() throws Exception {
        // when & then
        mockMvc.perform(get("/api/organizations/{organizationId}/reservations", organization.getId())
                        .param("status", "CONFIRMED")
                        .param("page", "0")
                        .param("size", "10")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(newestReservation.getId().toString()))
                .andExpect(jsonPath("$.content[0].status").value("CONFIRMED"));
    }

    @Test
    void exposesCustomerOffsetAndKeysetEndpoints() throws Exception {
        // when & then
        mockMvc.perform(get("/api/customers/{customerId}/reservations", customer.getId())
                        .param("page", "0")
                        .param("size", "1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(newestReservation.getId().toString()));

        mockMvc.perform(get("/api/customers/{customerId}/reservations/keyset", customer.getId())
                        .param("size", "1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id").value(newestReservation.getId().toString()))
                .andExpect(jsonPath("$.hasNext").value(true));
    }

    @Test
    void exposesStatsAndNPlusOneComparisonEndpoints() throws Exception {
        // when & then
        mockMvc.perform(get("/api/events/{eventId}/stats", event.getId())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(event.getId().toString()))
                .andExpect(jsonPath("$.totalReservations").value(2))
                .andExpect(jsonPath("$.confirmedReservations").value(1));

        mockMvc.perform(get("/api/events/{eventId}/reservations/fetch-join", event.getId())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].eventId").value(event.getId().toString()))
                .andExpect(jsonPath("$[0].customerEmail").value("stage5-api@example.com"));

        mockMvc.perform(get("/api/events/{eventId}/reservations/entity-graph", event.getId())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    private void cleanDatabase() {
        jdbcTemplate.update("delete from audit_logs");
        jdbcTemplate.update("delete from outbound_messages");
        jdbcTemplate.update("delete from reservations");
        jdbcTemplate.update("delete from capacity_pools");
        jdbcTemplate.update("delete from app_users");
        jdbcTemplate.update("delete from events");
        jdbcTemplate.update("delete from customers");
        jdbcTemplate.update("delete from organizations");
    }

    private void setCreatedAt(Reservation reservation, String instant) {
        jdbcTemplate.update(
                "update reservations set created_at = ? where id = ?",
                from(Instant.parse(instant)),
                reservation.getId()
        );
    }
}
