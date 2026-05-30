package pl.jakubtworek.booking.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import pl.jakubtworek.booking.dto.EventCreateRequest;
import pl.jakubtworek.booking.dto.EventResponse;
import pl.jakubtworek.booking.entity.EventStatus;
import pl.jakubtworek.booking.exception.NotFoundException;
import pl.jakubtworek.booking.service.EventService;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
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
class EventServiceIntegrationTest {
    @Autowired
    EventService eventService;

    @Test
    void createsEventWithCapacityPool() {
        EventResponse event = eventService.create(new EventCreateRequest(
                "Java Performance Workshop",
                "Warsaw",
                "education",
                OffsetDateTime.now().plusDays(30),
                50
        ));

        assertThat(event.id()).isNotNull();
        assertThat(event.name()).isEqualTo("Java Performance Workshop");
        assertThat(event.city()).isEqualTo("Warsaw");
        assertThat(event.category()).isEqualTo("education");
        assertThat(event.status()).isEqualTo(EventStatus.PUBLISHED);
        assertThat(event.totalCapacity()).isEqualTo(50);
        assertThat(event.availableCapacity()).isEqualTo(50);
    }

    @Test
    void returnsCreatedEventById() {
        EventResponse created = eventService.create(new EventCreateRequest(
                "Spring Internals Meetup",
                "Krakow",
                "technology",
                OffsetDateTime.now().plusDays(14),
                20
        ));

        EventResponse found = eventService.get(created.id());

        assertThat(found).usingRecursiveComparison().isEqualTo(created);
    }

    @Test
    void throwsNotFoundForMissingEvent() {
        UUID missingId = UUID.randomUUID();

        assertThatThrownBy(() -> eventService.get(missingId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Event not found");
    }
}
