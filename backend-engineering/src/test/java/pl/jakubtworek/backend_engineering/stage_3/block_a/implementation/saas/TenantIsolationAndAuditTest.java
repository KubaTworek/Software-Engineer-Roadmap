package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.saas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class TenantIsolationAndAuditTest {

    private static final TenantId ALPHA = new TenantId("alpha-co");
    private static final TenantId BETA = new TenantId("beta-co");

    @Test
    void sameBusinessIdentifierMustResolveInsideTenantBoundary() {
        TenantDataRepository repository = new TenantDataRepository();
        repository.save(new TenantDataRecord(ALPHA, "customer-1", "alpha@example.com", "Alpha"));
        repository.save(new TenantDataRecord(BETA, "customer-1", "beta@example.com", "Beta"));
        AccessAuditTrail audit = new AccessAuditTrail(fixedClock());
        TenantDataService service = new TenantDataService(repository, audit);

        TenantDataRecord result = service.read(
                new TenantRequestContext(ALPHA, "operator-7", "customer-support"),
                ALPHA,
                "customer-1");

        assertThat(result.email()).isEqualTo("alpha@example.com");
        assertThat(audit.events()).singleElement().satisfies(event -> {
            assertThat(event.tenantId()).isEqualTo(ALPHA);
            assertThat(event.purpose()).isEqualTo("customer-support");
            assertThat(event.outcome()).isEqualTo(AccessAuditTrail.Outcome.ALLOWED);
            assertThat(event.toString()).doesNotContain("alpha@example.com");
        });
    }

    @Test
    void crossTenantAttemptMustFailAndRemainAuditable() {
        TenantDataRepository repository = new TenantDataRepository();
        repository.save(new TenantDataRecord(BETA, "customer-1", "beta@example.com", "Beta"));
        AccessAuditTrail audit = new AccessAuditTrail(fixedClock());
        TenantDataService service = new TenantDataService(repository, audit);
        TenantRequestContext alphaActor = new TenantRequestContext(ALPHA, "operator-7", "customer-support");

        assertThatThrownBy(() -> service.read(alphaActor, BETA, "customer-1"))
                .isInstanceOf(TenantDataService.TenantIsolationException.class)
                .hasMessage("cross-tenant access denied");

        assertThat(audit.events()).singleElement().satisfies(event -> {
            assertThat(event.tenantId()).isEqualTo(ALPHA);
            assertThat(event.outcome()).isEqualTo(AccessAuditTrail.Outcome.DENIED);
        });
    }

    private static Clock fixedClock() {
        return Clock.fixed(Instant.parse("2026-01-15T10:00:00Z"), ZoneOffset.UTC);
    }
}
