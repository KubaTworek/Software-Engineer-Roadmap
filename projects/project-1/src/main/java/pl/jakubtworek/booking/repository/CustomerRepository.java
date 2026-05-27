package pl.jakubtworek.booking.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.jakubtworek.booking.entity.Customer;

import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {
    Optional<Customer> findByEmail(String email);
}
