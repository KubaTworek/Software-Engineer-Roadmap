package pl.jakubtworek.booking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

/*
 * Authentication jest realizowane przez własny filtr JWT i AuthService.
 * Wyłączenie domyślnego użytkownika Spring Boot zapobiega wygenerowaniu hasła,
 * które sugerowałoby istnienie drugiego, nieużywanego mechanizmu logowania.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
@EnableScheduling
public class BookingCapacityPlatformApplication {
    public static void main(String[] args) {
        SpringApplication.run(BookingCapacityPlatformApplication.class, args);
    }
}
