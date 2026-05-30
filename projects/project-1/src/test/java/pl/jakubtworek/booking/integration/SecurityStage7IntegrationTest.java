package pl.jakubtworek.booking.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import pl.jakubtworek.booking.security.PasswordHasher;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import pl.jakubtworek.booking.entity.*;
import pl.jakubtworek.booking.repository.*;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityStage7IntegrationTest {
    private static final String[] CLEANUP_SQL = {
            "DELETE FROM refresh_tokens",
            "DELETE FROM outbound_messages",
            "DELETE FROM audit_logs",
            "DELETE FROM reservations",
            "DELETE FROM capacity_pools",
            "DELETE FROM app_users",
            "DELETE FROM events",
            "DELETE FROM customers",
            "DELETE FROM organizations"
    };

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired PasswordHasher passwordHasher;
    @Autowired OrganizationRepository organizationRepository;
    @Autowired AppUserRepository appUserRepository;
    @Autowired EventRepository eventRepository;
    @Autowired CapacityPoolRepository capacityPoolRepository;
    @Autowired CustomerRepository customerRepository;
    @Autowired ReservationRepository reservationRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;

    private Organization orgA;
    private Organization orgB;
    private Event orgAEvent;
    private Reservation orgAReservation;

    @BeforeEach
    void setUp() {
        for (String sql : CLEANUP_SQL) {
            jdbcTemplate.execute(sql);
        }
        orgA = organizationRepository.save(new Organization("Org A"));
        orgB = organizationRepository.save(new Organization("Org B"));
        orgAEvent = eventRepository.save(new Event(orgA, "Security Conf", "Warsaw", "tech", OffsetDateTime.now().plusDays(30)));
        capacityPoolRepository.save(new CapacityPool(orgAEvent, 10));
        Customer customer = customerRepository.save(new Customer("Customer One", "customer@example.com"));
        orgAReservation = reservationRepository.save(new Reservation(orgAEvent, customer));

        saveUser(orgA, "customer@example.com", "secret", UserRole.CUSTOMER);
        saveUser(orgA, "manager@orga.com", "secret", UserRole.EVENT_MANAGER);
        saveUser(orgA, "admin@orga.com", "secret", UserRole.ORG_ADMIN);
        saveUser(orgA, "hr@orga.com", "secret", UserRole.HR);
        saveUser(null, "support@example.com", "secret", UserRole.SUPPORT);
        saveUser(orgB, "manager@orgb.com", "secret", UserRole.EVENT_MANAGER);
        saveUser(orgB, "admin@orgb.com", "secret", UserRole.ORG_ADMIN);
    }

    @Test
    void loginIssuesJwtAccessTokenAndRefreshToken() throws Exception {
        JsonNode response = login("manager@orga.com", "secret");

        assertThat(response.get("tokenType").asText()).isEqualTo("Bearer");
        assertThat(response.get("accessToken").asText()).contains(".");
        assertThat(response.get("refreshToken").asText()).isNotBlank();
    }

    @Test
    void refreshRotatesRefreshTokenAndRevokesPreviousToken() throws Exception {
        JsonNode login = login("manager@orga.com", "secret");
        String oldRefreshToken = login.get("refreshToken").asText();

        JsonNode refreshed = refresh(oldRefreshToken);
        String newRefreshToken = refreshed.get("refreshToken").asText();

        assertThat(newRefreshToken).isNotEqualTo(oldRefreshToken);
        assertThat(refreshTokenRepository.findAll()).hasSize(2);
        assertThat(refreshTokenRepository.findAll().stream().filter(token -> token.getRevokedAt() != null)).hasSize(1);

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(oldRefreshToken)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void customerCanViewOnlyOwnReservation() throws Exception {
        String customerToken = login("customer@example.com", "secret").get("accessToken").asText();
        String otherCustomerToken = tokenForNewUser(orgA, "other-customer@example.com", UserRole.CUSTOMER);

        mockMvc.perform(get("/api/secure/reservations/{id}", orgAReservation.getId())
                        .header("Authorization", bearer(customerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orgAReservation.getId().toString()));

        mockMvc.perform(get("/api/secure/reservations/{id}", orgAReservation.getId())
                        .header("Authorization", bearer(otherCustomerToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void eventManagerCanManageOnlyEventsFromOwnOrganization() throws Exception {
        String orgAManagerToken = login("manager@orga.com", "secret").get("accessToken").asText();
        String orgBManagerToken = login("manager@orgb.com", "secret").get("accessToken").asText();

        mockMvc.perform(get("/api/secure/events/{id}/manager-view", orgAEvent.getId())
                        .header("Authorization", bearer(orgAManagerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orgAEvent.getId().toString()));

        mockMvc.perform(get("/api/secure/events/{id}/manager-view", orgAEvent.getId())
                        .header("Authorization", bearer(orgBManagerToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void orgAdminCanManageUsersOnlyInsideOwnTenant() throws Exception {
        String orgAAdminToken = login("admin@orga.com", "secret").get("accessToken").asText();
        String orgBAdminToken = login("admin@orgb.com", "secret").get("accessToken").asText();

        mockMvc.perform(get("/api/secure/organizations/{id}/users", orgA.getId())
                        .header("Authorization", bearer(orgAAdminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].organizationId").value(orgA.getId().toString()));

        mockMvc.perform(get("/api/secure/organizations/{id}/users", orgA.getId())
                        .header("Authorization", bearer(orgBAdminToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void hrCanReadEmployeesOnlyFromOwnOrganization() throws Exception {
        String hrToken = login("hr@orga.com", "secret").get("accessToken").asText();

        mockMvc.perform(get("/api/secure/hr/organizations/{id}/employees", orgA.getId())
                        .header("Authorization", bearer(hrToken)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/secure/hr/organizations/{id}/employees", orgB.getId())
                        .header("Authorization", bearer(hrToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void supportCanSeeMaskedPaymentSummaryButNotFullPaymentData() throws Exception {
        String supportToken = login("support@example.com", "secret").get("accessToken").asText();

        mockMvc.perform(get("/api/secure/support/reservations/{id}/payment-summary", orgAReservation.getId())
                        .header("Authorization", bearer(supportToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cardNumber").value("**** **** **** ****"));

        mockMvc.perform(get("/api/secure/support/reservations/{id}/full-payment-data", orgAReservation.getId())
                        .header("Authorization", bearer(supportToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void secureEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/secure/reservations/{id}", orgAReservation.getId()))
                .andExpect(status().isForbidden());
    }

    private AppUser saveUser(Organization organization, String email, String password, UserRole role) {
        return appUserRepository.save(new AppUser(organization, email, passwordHasher.encode(password), role));
    }

    private JsonNode login(String email, String password) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response);
    }

    private JsonNode refresh(String refreshToken) throws Exception {
        String response = mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(refreshToken)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response);
    }

    private String tokenForNewUser(Organization organization, String email, UserRole role) throws Exception {
        saveUser(organization, email, "secret", role);
        return login(email, "secret").get("accessToken").asText();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
