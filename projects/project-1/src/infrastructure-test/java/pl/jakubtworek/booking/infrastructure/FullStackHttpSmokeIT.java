package pl.jakubtworek.booking.infrastructure;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import pl.jakubtworek.booking.entity.AppUser;
import pl.jakubtworek.booking.entity.Organization;
import pl.jakubtworek.booking.entity.UserRole;
import pl.jakubtworek.booking.repository.AppUserRepository;
import pl.jakubtworek.booking.repository.OrganizationRepository;
import pl.jakubtworek.booking.security.PasswordHasher;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test uruchamia cały proces Spring Boot i prawdziwe adaptery. Oddzielne
 * testy kontraktów mówią, że PostgreSQL/Redis/Mongo zachowują się poprawnie;
 * ten scenariusz dowodzi, że konfiguracja, security, HTTP i trzy magazyny dają
 * się złożyć w jeden przepływ po migracji frameworka.
 */
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("nosql-real")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "security.jwt.secret=full-stack-smoke-secret-with-more-than-32-characters",
                "spring.task.scheduling.enabled=false"
        }
)
class FullStackHttpSmokeIT {
    private static final String PASSWORD = "correct horse battery staple";

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("booking_capacity")
            .withUsername("booking")
            .withPassword("booking");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @Container
    static final GenericContainer<?> MONGO = new GenericContainer<>(DockerImageName.parse("mongo:7"))
            .withExposedPorts(27017);

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
        properties.add("spring.data.redis.host", REDIS::getHost);
        properties.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        properties.add("spring.mongodb.uri", () ->
                "mongodb://" + MONGO.getHost() + ":" + MONGO.getMappedPort(27017)
                        + "/booking_capacity_read_model");
    }

    @LocalServerPort
    int port;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    OrganizationRepository organizationRepository;

    @Autowired
    AppUserRepository appUserRepository;

    @Autowired
    PasswordHasher passwordHasher;

    @Test
    void authenticatedReservationFlowsFromHttpThroughPostgresRedisAndMongo() throws Exception {
        Organization organization = organizationRepository.save(new Organization("Full-stack smoke organization"));
        appUserRepository.save(new AppUser(
                organization,
                "manager-smoke@example.test",
                passwordHasher.encode(PASSWORD),
                UserRole.EVENT_MANAGER
        ));
        appUserRepository.save(new AppUser(
                null,
                "customer-smoke@example.test",
                passwordHasher.encode(PASSWORD),
                UserRole.CUSTOMER
        ));

        try (HttpClient client = HttpClient.newHttpClient()) {
            String managerToken = login(client, "manager-smoke@example.test");
            String customerToken = login(client, "customer-smoke@example.test");

            JsonNode event = postJson(client, "/api/events", managerToken, objectMapper.createObjectNode()
                    .put("name", "Full-stack smoke event")
                    .put("city", "Warsaw")
                    .put("category", "education")
                    .put("startsAt", OffsetDateTime.now(ZoneOffset.UTC).plusDays(30).toString())
                    .put("totalCapacity", 2), 201);
            String eventId = event.get("id").stringValue();

            postJson(client, "/api/events/" + eventId + "/reservations", customerToken,
                    objectMapper.createObjectNode()
                            .put("customerFullName", "Smoke Customer")
                            .put("customerEmail", "customer-smoke@example.test"), 201);

            JsonNode eventAfterReservation = getJson(client, "/api/events/" + eventId, null, 200);
            assertThat(eventAfterReservation.get("availableCapacity").asInt()).isEqualTo(1);

            postJson(client, "/api/nosql/read-model/events/" + eventId + "/rebuild", null, null, 200);
            JsonNode readModel = getJson(client, "/api/nosql/read-model/events/" + eventId, null, 200);
            assertThat(readModel.get("availableCapacity").asInt()).isEqualTo(1);

            JsonNode cachedEvent = getJson(client, "/api/nosql/cache/events/" + eventId, null, 200);
            assertThat(cachedEvent.get("eventId").stringValue()).isEqualTo(eventId);
        }
    }

    private String login(HttpClient client, String email) throws Exception {
        JsonNode response = postJson(client, "/api/auth/login", null, objectMapper.createObjectNode()
                .put("email", email)
                .put("password", PASSWORD), 200);
        return response.get("accessToken").stringValue();
    }

    private JsonNode postJson(HttpClient client, String path, String token, JsonNode body, int expectedStatus)
            throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .POST(body == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(expectedStatus);
        return response.body().isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(response.body());
    }

    private JsonNode getJson(HttpClient client, String path, String token, int expectedStatus) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri(path)).GET();
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(expectedStatus);
        return objectMapper.readTree(response.body());
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }
}
