package pl.jakubtworek.backend.order.application;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import pl.jakubtworek.backend.order.api.CreateOrderRequest;
import pl.jakubtworek.backend.order.client.*;
import pl.jakubtworek.backend.order.domain.*;
import pl.jakubtworek.backend.order.outbox.*;
import pl.jakubtworek.backend.order.repository.OrderRepository;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Rzeczywisty błąd COMMITu PostgreSQL, zewnętrzne efekty celowo poza transakcją JPA. */
@Tag("infrastructure") @Testcontainers
@SpringBootTest(classes = OrderCommitRecoveryInfrastructureTest.Config.class,
        properties = {"spring.main.web-application-type=none", "spring.jpa.hibernate.ddl-auto=validate"})
class OrderCommitRecoveryInfrastructureTest {
    @SpringBootConfiguration @EnableAutoConfiguration
    @EntityScan(basePackageClasses = {OrderEntity.class, OutboxMessage.class})
    @EnableJpaRepositories(basePackageClasses = {OrderRepository.class, OutboxRepository.class})
    @Import(OrderService.class)
    static class Config {
        @Bean Clock clock() { return Clock.systemUTC(); }
        @Bean tools.jackson.databind.ObjectMapper mapper() { return new tools.jackson.databind.ObjectMapper(); }
    }
    @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
    }
    @Autowired OrderService service;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean PaymentClient payments;
    @MockitoBean ReservationClient reservations;

    @Test void intentFailurePreventsChargeAndExecutionFailureRemainsRecoverable() {
        UUID reservation = UUID.randomUUID();
        var request = new CreateOrderRequest(reservation, "commit-test");
        Set<UUID> externalLedger = new HashSet<>();
        when(reservations.get(reservation)).thenReturn(reservation(reservation, "PENDING"));
        when(reservations.confirm(reservation)).thenReturn(reservation(reservation, "CONFIRMED"));
        when(payments.pay(any(), any(), any())).thenAnswer(call -> {
            UUID order = call.getArgument(0);
            externalLedger.add(order);
            return new PaymentClient.PaymentResponse(UUID.randomUUID(), order, "PAID");
        });
        jdbc.execute("""
                create function reject_audit_commit() returns trigger language plpgsql as $$
                begin raise exception 'injected deferred commit failure'; return new; end $$
                """);
        jdbc.execute("create constraint trigger audit_reject after insert on orders deferrable initially deferred for each row execute function reject_audit_commit()");
        try { assertThatThrownBy(() -> service.create(request, "commit-key")).isInstanceOf(RuntimeException.class); }
        finally { jdbc.execute("drop trigger audit_reject on orders"); }
        assertThat(externalLedger).isEmpty();
        verifyNoInteractions(payments);
        jdbc.execute("create constraint trigger audit_reject after update on orders deferrable initially deferred for each row when (new.status='PAID') execute function reject_audit_commit()");
        try { assertThatThrownBy(() -> service.create(request, "commit-key")).isInstanceOf(RuntimeException.class); }
        finally { jdbc.execute("drop trigger audit_reject on orders"); }
        assertThat(externalLedger).hasSize(1);
        assertThat(jdbc.queryForObject("select status from orders where idempotency_key='commit-key'", String.class)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("select count(*) from outbox_messages", Integer.class)).isZero();
        var recovered = service.create(request, "commit-key");
        assertThat(recovered.status()).isEqualTo(OrderStatus.PAID);
        assertThat(externalLedger).containsExactly(recovered.id());
        assertThatThrownBy(() -> service.create(request, "other-key-same-reservation"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(payments, times(2)).pay(eq(recovered.id()), any(), any());
        assertThat(jdbc.queryForObject("select count(*) from outbox_messages", Integer.class)).isEqualTo(1);
    }
    private ReservationClient.ReservationResponse reservation(UUID id, String status) {
        return new ReservationClient.ReservationResponse(id, UUID.randomUUID(), "commit-test", 1,
                status, Instant.now().plusSeconds(300), Instant.now(), Instant.now());
    }
}
