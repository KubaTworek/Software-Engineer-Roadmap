package pl.jakubtworek.booking.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import pl.jakubtworek.booking.entity.Customer;
import pl.jakubtworek.booking.repository.CustomerRepository;

import java.time.Clock;
import java.sql.Timestamp;
import java.sql.SQLException;
import java.util.UUID;
import javax.sql.DataSource;

/**
 * Rozwiązuje wyścig „find-or-create” dla klienta identyfikowanego e-mailem.
 *
 * <p>Sam kod {@code findByEmail().orElseGet(save)} jest check-then-act: dwie
 * transakcje mogą nie znaleźć rekordu i obie spróbują go utworzyć. Unikalny
 * constraint jest źródłem prawdy. {@code ON CONFLICT DO NOTHING} rozstrzyga
 * wyścig wewnątrz bieżącej transakcji bez wyjątku oznaczającego ją jako
 * rollback-only i bez zagnieżdżonego połączenia z puli.</p>
 */
@Service
public class CustomerRegistrationService {
    private final CustomerRepository customerRepository;
    private final JdbcTemplate jdbc;
    private final boolean postgreSql;
    private final Clock clock;

    public CustomerRegistrationService(
            CustomerRepository customerRepository,
            JdbcTemplate jdbc,
            DataSource dataSource,
            Clock clock
    ) {
        this.customerRepository = customerRepository;
        this.jdbc = jdbc;
        this.postgreSql = identifyPostgreSql(dataSource);
        this.clock = clock;
    }

    public Customer resolve(String fullName, String email) {
        return customerRepository.findByEmail(email).orElseGet(() -> insertOrReadWinner(fullName, email));
    }

    private Customer insertOrReadWinner(String fullName, String email) {
        if (!postgreSql) {
            /*
             * H2 jest szybkim adapterem testowym, ale nie emuluje PostgreSQL
             * ON CONFLICT. Testy wyścigu należą do profilu z prawdziwym
             * PostgreSQL; w zwykłej suite wystarcza ścieżka JPA.
             */
            return customerRepository.save(new Customer(fullName, email, clock.instant()));
        }
        UUID candidateId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO customers(id, full_name, email, created_at)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (email) DO NOTHING
                """, candidateId, fullName, email, Timestamp.from(clock.instant()));

        // Jeżeli INSERT przegrał, PostgreSQL czeka na rozstrzygnięcie konfliktu
        // unikalności. Następujący SELECT widzi więc jeden zwycięski rekord.
        return customerRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Customer upsert did not produce a row: " + email));
    }

    private boolean identifyPostgreSql(DataSource dataSource) {
        try (var connection = dataSource.getConnection()) {
            return connection.getMetaData().getDatabaseProductName().toLowerCase().contains("postgresql");
        } catch (SQLException failure) {
            throw new IllegalStateException("Cannot identify database dialect", failure);
        }
    }
}
