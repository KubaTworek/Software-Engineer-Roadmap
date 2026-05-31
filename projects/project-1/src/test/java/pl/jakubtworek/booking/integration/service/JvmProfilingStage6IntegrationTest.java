package pl.jakubtworek.booking.integration.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import pl.jakubtworek.booking.dto.profiling.OrganizationReportProfilingResponse;
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
import pl.jakubtworek.booking.service.profiling.JvmProfilingService;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class JvmProfilingStage6IntegrationTest {
    @Autowired JvmProfilingService profilingService;
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
        organization = organizationRepository.save(new Organization("Stage 6 Org"));
        event = eventRepository.save(new Event(
                organization,
                "Stage 6 Profiling Event",
                "Warsaw",
                "performance",
                OffsetDateTime.of(2026, 8, 1, 18, 0, 0, 0, ZoneOffset.UTC)
        ));
        capacityPoolRepository.save(new CapacityPool(event, 200));
    }

    @Test
    void runsShortReservationScenario() {
        // given & when
        var result = profilingService.runShortReservationBurst(event.getId(), 5);

        // then
        assertThat(result.requestedReservations()).isEqualTo(5);
        assertThat(result.successfulReservations()).isEqualTo(5);
        assertThat(result.failedReservations()).isZero();
        assertThat(result.elapsedMillis()).isPositive();
        assertThat(result.bottleneckToObserve()).contains("JFR");
    }

    @Test
    void runsParallelReservationScenario() {
        // given & when
        var result = profilingService.runParallelReservationBurst(event.getId(), 12, 12);

        // then
        assertThat(result.requestedReservations()).isEqualTo(12);
        assertThat(result.successfulReservations()).isEqualTo(12);
        assertThat(result.failedReservations()).isZero();
        assertThat(result.elapsedMillis()).isPositive();
    }

    @Test
    void buildsOrganizationReportScenario() {
        // given
        Customer customer = customerRepository.save(new Customer("Report User", "report.stage6@example.com"));
        Reservation pending = reservationRepository.save(new Reservation(event, customer));
        Reservation confirmed = new Reservation(event, customer);
        confirmed.confirm();
        reservationRepository.save(confirmed);

        // when
        OrganizationReportProfilingResponse response = profilingService.organizationReport(organization.getId());

        // then
        assertThat(response.organizationId()).isEqualTo(organization.getId());
        assertThat(response.totalReservations()).isEqualTo(2);
        assertThat(response.reservationsByStatus()).containsEntry("PENDING", 1L);
        assertThat(response.reservationsByStatus()).containsEntry("CONFIRMED", 1L);
        assertThat(pending.getOrganization().getId()).isEqualTo(organization.getId());
    }

    @Test
    void runsAllocationPressureScenario() {
        // given & when
        var result = profilingService.allocationPressure(1000);

        // then
        assertThat(result.objectsCreated()).isEqualTo(1000);
        assertThat(result.approximatePayloadBytes()).isPositive();
        assertThat(result.bottleneckToObserve()).contains("allocation");
    }

    @Test
    void runsLockContentionScenario() {
        // given & when
        var result = profilingService.lockContention(4, 1000);

        // then
        assertThat(result.threads()).isEqualTo(4);
        assertThat(result.incrementsPerThread()).isEqualTo(1000);
        assertThat(result.finalValue()).isEqualTo(4000);
        assertThat(result.bottleneckToObserve()).contains("JFR");
    }

    @Test
    void runsThreadPoolAndNumericAllocationScenarios() {
        // given & when
        var cpu = profilingService.threadPoolExperiment(2, 4, "CPU");
        var io = profilingService.threadPoolExperiment(2, 4, "IO");
        var bigDecimal = profilingService.numericAllocationScenario(1000);

        // then
        assertThat(cpu.tasks()).isEqualTo(4);
        assertThat(io.workloadType()).isEqualTo("IO");
        assertThat(bigDecimal.operations()).isEqualTo(1000);
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
