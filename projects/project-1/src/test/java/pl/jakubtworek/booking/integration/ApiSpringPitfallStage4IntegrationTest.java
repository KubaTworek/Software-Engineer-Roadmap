package pl.jakubtworek.booking.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class ApiSpringPitfallStage4IntegrationTest {
    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    String reservationId;
    String eventId;

    @BeforeEach
    void setUp() throws Exception {
        String eventJson = mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EventCreateRequest(
                                "Spring Pitfalls Workshop",
                                "Warsaw",
                                "education",
                                OffsetDateTime.now().plusDays(30),
                                10
                        ))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        eventId = objectMapper.readTree(eventJson).get("id").asText();

        String reservationJson = mockMvc.perform(post("/api/events/{eventId}/reservations", eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReservationCreateRequest(
                                "Stage Four User",
                                "stage4@example.com"
                        ))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        reservationId = objectMapper.readTree(reservationJson).get("id").asText();
    }

    @Test
    void transactionalEndpointShowsSelfInvocationProblem() throws Exception {
        mockMvc.perform(get("/api/spring-pitfalls/transactional/self-invocation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionActiveWhenCalledThroughThis").value(false))
                .andExpect(jsonPath("$.transactionActiveWhenCalledThroughProxy").value(true));
    }

    @Test
    void lazyBrokenEndpointFailsOutsidePersistenceContext() throws Exception {
        mockMvc.perform(get("/api/spring-pitfalls/reservations/{reservationId}/lazy-broken", reservationId))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("LAZY_INITIALIZATION"));
    }

    @Test
    void lazyLoadingCanBeFixedByMappingInsideTransaction() throws Exception {
        mockMvc.perform(get("/api/spring-pitfalls/reservations/{reservationId}/lazy-fixed-transaction", reservationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservationId").value(reservationId))
                .andExpect(jsonPath("$.eventId").value(eventId))
                .andExpect(jsonPath("$.eventName").value("Spring Pitfalls Workshop"))
                .andExpect(jsonPath("$.customerEmail").value("stage4@example.com"));
    }

    @Test
    void lazyLoadingCanBeAvoidedWithDtoProjection() throws Exception {
        mockMvc.perform(get("/api/spring-pitfalls/reservations/{reservationId}/lazy-fixed-projection", reservationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservationId").value(reservationId))
                .andExpect(jsonPath("$.eventName").value("Spring Pitfalls Workshop"));
    }

    @Test
    void lazyLoadingCanBeFixedWithFetchJoin() throws Exception {
        mockMvc.perform(get("/api/spring-pitfalls/reservations/{reservationId}/lazy-fixed-fetch-join", reservationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservationId").value(reservationId))
                .andExpect(jsonPath("$.eventName").value("Spring Pitfalls Workshop"));
    }

    @Test
    void lazyLoadingCanBeFixedWithEntityGraph() throws Exception {
        mockMvc.perform(get("/api/spring-pitfalls/reservations/{reservationId}/lazy-fixed-entity-graph", reservationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservationId").value(reservationId))
                .andExpect(jsonPath("$.eventName").value("Spring Pitfalls Workshop"));
    }

    @Test
    void aopEndpointShowsProxyBoundary() throws Exception {
        mockMvc.perform(post("/api/spring-pitfalls/aop/through-this")
                        .param("input", "spring"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("SPRING"))
                .andExpect(jsonPath("$.measurementCount").value(0));

        mockMvc.perform(post("/api/spring-pitfalls/aop/through-proxy")
                        .param("input", "spring"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("SPRING"))
                .andExpect(jsonPath("$.measurementCount").value(1))
                .andExpect(jsonPath("$.measurements[0].methodName").value("MeasuredPitfallService.measuredOperation"));
    }

    @Test
    void beanLifecycleEndpointShowsSingletonAndInitializationCallbacks() throws Exception {
        mockMvc.perform(get("/api/spring-pitfalls/bean-lifecycle"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sameSingletonInstance").value(true))
                .andExpect(jsonPath("$.postConstructCalled").value(true))
                .andExpect(jsonPath("$.afterPropertiesSetCalled").value(true))
                .andExpect(jsonPath("$.preDestroyCalled").value(false));
    }
}
