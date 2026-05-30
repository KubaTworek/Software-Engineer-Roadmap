package pl.jakubtworek.booking.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import pl.jakubtworek.booking.dto.ReservationCreateRequest;
import pl.jakubtworek.booking.dto.nosql.EventSearchDocumentListResponse;
import pl.jakubtworek.booking.dto.nosql.EventSearchDocumentResponse;
import pl.jakubtworek.booking.entity.CapacityPool;
import pl.jakubtworek.booking.entity.Event;
import pl.jakubtworek.booking.entity.Organization;
import pl.jakubtworek.booking.repository.CapacityPoolRepository;
import pl.jakubtworek.booking.repository.EventRepository;
import pl.jakubtworek.booking.repository.OrganizationRepository;
import pl.jakubtworek.booking.service.EventSearchReadModelService;
import pl.jakubtworek.booking.service.ReservationService;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EventReadModelStage8IntegrationTest {
    @Autowired EventSearchReadModelService readModelService;
    @Autowired ReservationService reservationService;
    @Autowired EventRepository eventRepository;
    @Autowired OrganizationRepository organizationRepository;
    @Autowired CapacityPoolRepository capacityPoolRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    private Event event;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        readModelService.clear();

        Organization organization = organizationRepository.save(new Organization("Stage 8 Org"));
        event = eventRepository.save(new Event(
                organization,
                "Mongo Read Model Event",
                "Warsaw",
                "music",
                OffsetDateTime.of(2026, 10, 1, 20, 0, 0, 0, ZoneOffset.UTC)
        ));
        capacityPoolRepository.save(new CapacityPool(event, 20));
        reservationService.create(event.getId(), new ReservationCreateRequest("Read Model Customer", "read-model@example.com"));
    }

    @Test
    void rebuildsDenormalizedEventSearchDocumentFromRelationalSourceOfTruth() {
        EventSearchDocumentResponse document = readModelService.rebuildOne(event.getId());

        assertThat(document.eventId()).isEqualTo(event.getId());
        assertThat(document.name()).isEqualTo("Mongo Read Model Event");
        assertThat(document.organizationName()).isEqualTo("Stage 8 Org");
        assertThat(document.totalCapacity()).isEqualTo(20);
        assertThat(document.availableCapacity()).isEqualTo(19);
        assertThat(document.reservationsByStatus()).containsEntry("PENDING", 1L);
    }

    @Test
    void searchUsesDenormalizedReadModelAndCanBeStaleUntilRebuild() {
        EventSearchDocumentResponse firstSnapshot = readModelService.rebuildOne(event.getId());
        assertThat(firstSnapshot.availableCapacity()).isEqualTo(19);

        reservationService.create(event.getId(), new ReservationCreateRequest("Second Customer", "read-model-2@example.com"));

        EventSearchDocumentResponse staleDocument = readModelService.get(event.getId());
        assertThat(staleDocument.availableCapacity()).isEqualTo(19);

        EventSearchDocumentResponse rebuiltDocument = readModelService.rebuildOne(event.getId());
        assertThat(rebuiltDocument.availableCapacity()).isEqualTo(18);
        assertThat(rebuiltDocument.reservationsByStatus()).containsEntry("PENDING", 2L);
    }

    @Test
    void searchesReadModelByAccessPatternInsteadOfRelationalJoins() {
        readModelService.rebuildOne(event.getId());

        EventSearchDocumentListResponse result = readModelService.search("Warsaw", "music", 10);

        assertThat(result.size()).isEqualTo(1);
        assertThat(result.items()).extracting(EventSearchDocumentResponse::eventId).containsExactly(event.getId());
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
