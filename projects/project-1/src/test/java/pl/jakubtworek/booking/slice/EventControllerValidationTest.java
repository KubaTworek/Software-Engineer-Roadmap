package pl.jakubtworek.booking.slice;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.TestPropertySource;
import pl.jakubtworek.booking.controller.EventController;
import pl.jakubtworek.booking.security.JwtTokenService;
import pl.jakubtworek.booking.service.EventService;
import pl.jakubtworek.booking.service.SqlPerformanceService;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice ładuje wyłącznie adapter MVC. Walidacja requestu nie wymaga bazy,
 * Hibernate, Redisa ani pełnego kontekstu aplikacji.
 */
@WebMvcTest(controllers = EventController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = "security.jwt.secret=slice-test-secret-at-least-thirty-two-characters")
class EventControllerValidationTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean EventService eventService;
    @MockitoBean SqlPerformanceService sqlPerformanceService;
    @MockitoBean JwtTokenService jwtTokenService;

    @Test
    void rejectsInvalidContractBeforeCallingUseCase() throws Exception {
        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"", "city":"", "category":"education",
                                 "startsAt":"2020-01-01T10:00:00Z", "totalCapacity":0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
