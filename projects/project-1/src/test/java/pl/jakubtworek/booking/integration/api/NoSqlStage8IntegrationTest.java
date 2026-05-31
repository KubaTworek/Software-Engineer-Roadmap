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
import pl.jakubtworek.booking.entity.CapacityPool;
import pl.jakubtworek.booking.entity.Event;
import pl.jakubtworek.booking.repository.CapacityPoolRepository;
import pl.jakubtworek.booking.repository.EventRepository;
import pl.jakubtworek.booking.service.EventSearchReadModelService;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NoSqlStage8IntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired EventRepository eventRepository;
    @Autowired CapacityPoolRepository capacityPoolRepository;
    @Autowired EventSearchReadModelService readModelService;
    @Autowired JdbcTemplate jdbcTemplate;

    private Event event;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        readModelService.clear();
        event = eventRepository.save(new Event(
                "NoSQL API Event",
                "Warsaw",
                "music",
                OffsetDateTime.of(2026, 11, 1, 20, 0, 0, 0, ZoneOffset.UTC)
        ));
        capacityPoolRepository.save(new CapacityPool(event, 30));
    }

    @Test
    void exposesEventDetailsCacheAndAvailabilitySnapshot() throws Exception {
        // when & then
        mockMvc.perform(get("/api/nosql/cache/events/{eventId}", event.getId())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(event.getId().toString()))
                .andExpect(jsonPath("$.source").value("SQL"))
                .andExpect(jsonPath("$.availableCapacity").value(30));

        mockMvc.perform(get("/api/nosql/cache/events/{eventId}", event.getId())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("CACHE"));

        mockMvc.perform(get("/api/nosql/cache/events/{eventId}/availability", event.getId())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(event.getId().toString()))
                .andExpect(jsonPath("$.availableCapacity").value(30));
    }

    @Test
    void exposesReservationHoldAndRateLimiter() throws Exception {
        // given
        String holdLocation = mockMvc.perform(post("/api/nosql/cache/reservation-holds")
                        .param("eventId", event.getId().toString())
                        .param("customerEmail", "api-hold@example.com")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(event.getId().toString()))
                .andExpect(jsonPath("$.active").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(holdLocation).contains("api-hold@example.com");

        // when
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/nosql/cache/rate-limit/{clientKey}", "api-client")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.allowed").value(true));
        }

        // then
        mockMvc.perform(post("/api/nosql/cache/rate-limit/{clientKey}", "api-client")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(false));
    }

    @Test
    void exposesEventSearchReadModelEndpoints() throws Exception {
        // given
        mockMvc.perform(post("/api/nosql/read-model/events/{eventId}/rebuild", event.getId())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(event.getId().toString()))
                .andExpect(jsonPath("$.name").value("NoSQL API Event"));

        // when & then
        mockMvc.perform(get("/api/nosql/read-model/events/{eventId}", event.getId())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(event.getId().toString()));

        mockMvc.perform(get("/api/nosql/read-model/events")
                        .param("city", "Warsaw")
                        .param("category", "music")
                        .param("limit", "10")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].eventId").value(event.getId().toString()));
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
}
