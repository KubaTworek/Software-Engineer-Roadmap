package pl.jakubtworek.booking.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.jakubtworek.booking.entity.Organization;

import java.util.Optional;
import java.util.UUID;

public interface OrganizationRepository extends JpaRepository<Organization, UUID> {
    Optional<Organization> findByName(String name);
}
