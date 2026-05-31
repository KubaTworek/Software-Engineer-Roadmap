package pl.jakubtworek.booking.integration.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import pl.jakubtworek.booking.entity.CapacityPool;
import pl.jakubtworek.booking.entity.Customer;
import pl.jakubtworek.booking.entity.Event;
import pl.jakubtworek.booking.entity.Organization;
import pl.jakubtworek.booking.entity.Reservation;
import pl.jakubtworek.booking.repository.CapacityPoolRepository;
import pl.jakubtworek.booking.repository.CustomerRepository;
import pl.jakubtworek.booking.repository.EventRepository;
import pl.jakubtworek.booking.repository.OrganizationRepository;
import pl.jakubtworek.booking.repository.ReservationRepository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JvmProfilingStage6IntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired OrganizationRepository organizationRepository;
    @Autowired EventRepository eventRepository;
    @Autowired CapacityPoolRepository capacityPoolRepository;
    @Autowired CustomerRepository customerRepository;
    @Autowired ReservationRepository reservationRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    private Organization organization;
    private Event event;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        organization = organizationRepository.save(new Organization("Stage 6 API Org"));
        event = eventRepository.save(new Event(
                organization,
                "Stage 6 API Event",
                "Warsaw",
                "performance",
                OffsetDateTime.of(2026, 8, 2, 18, 0, 0, 0, ZoneOffset.UTC)
        ));
        capacityPoolRepository.save(new CapacityPool(event, 200));
        Customer customer = customerRepository.save(new Customer("API Report User", "api-report.stage6@example.com"));
        Reservation reservation = new Reservation(event, customer);
        reservation.confirm();
        reservationRepository.save(reservation);
    }

    @Test
    void exposesShortReservationProfilingEndpoint() throws Exception {
        // when & then
        mockMvc.perform(post("/api/profiling/events/{eventId}/short-reservations", event.getId())
                        .queryParam("requests", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(event.getId().toString()))
                .andExpect(jsonPath("$.requestedReservations").value(3))
                .andExpect(jsonPath("$.successfulReservations").value(3));
    }

    @Test
    void exposesParallelReservationProfilingEndpoint() throws Exception {
        // when & then
        mockMvc.perform(post("/api/profiling/events/{eventId}/parallel-reservations", event.getId())
                        .queryParam("requests", "4")
                        .queryParam("threads", "4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestedReservations").value(4))
                .andExpect(jsonPath("$.successfulReservations").value(4));
    }

    @Test
    void exposesOrganizationReportEndpoint() throws Exception {
        // when & then
        mockMvc.perform(get("/api/profiling/organizations/{organizationId}/report", organization.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizationId").value(organization.getId().toString()))
                .andExpect(jsonPath("$.totalReservations").value(1))
                .andExpect(jsonPath("$.reservationsByStatus.CONFIRMED").value(1));
    }

    @Test
    void exposesSyntheticProfilingEndpoints() throws Exception {
        // when & then
        mockMvc.perform(get("/api/profiling/allocations").queryParam("objects", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objectsCreated").value(100));

        mockMvc.perform(get("/api/profiling/lock-contention")
                        .queryParam("threads", "2")
                        .queryParam("incrementsPerThread", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.finalValue").value(200));

        mockMvc.perform(get("/api/profiling/thread-pool")
                        .queryParam("threads", "2")
                        .queryParam("tasks", "4")
                        .queryParam("workloadType", "CPU"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks").value(4));

        mockMvc.perform(get("/api/profiling/big-decimal-allocation")
                        .queryParam("iterations", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operations").value(100));
    }

    private void cleanDatabase() {
        jdbcTemplate.update("delete from outbound_messages");
        jdbcTemplate.update("delete from audit_logs");
        jdbcTemplate.update("delete from reservations");
        jdbcTemplate.update("delete from capacity_pools");
        jdbcTemplate.update("delete from app_users");
        jdbcTemplate.update("delete from events");
        jdbcTemplate.update("delete from customers");
        jdbcTemplate.update("delete from organizations");
    }
}
