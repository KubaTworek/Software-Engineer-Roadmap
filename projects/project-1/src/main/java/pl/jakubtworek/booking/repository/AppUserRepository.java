package pl.jakubtworek.booking.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.jakubtworek.booking.entity.AppUser;

import java.util.UUID;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {
}
