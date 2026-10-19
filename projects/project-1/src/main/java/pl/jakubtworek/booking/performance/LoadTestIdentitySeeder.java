package pl.jakubtworek.booking.performance;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pl.jakubtworek.booking.entity.AppUser;
import pl.jakubtworek.booking.entity.Organization;
import pl.jakubtworek.booking.entity.UserRole;
import pl.jakubtworek.booking.repository.AppUserRepository;
import pl.jakubtworek.booking.repository.OrganizationRepository;
import pl.jakubtworek.booking.security.PasswordHasher;

/**
 * Przygotowuje wyłącznie tożsamość potrzebną do powtarzalnego eksperymentu k6.
 * Profil {@code load-test} nigdy nie jest aktywny przy zwykłym uruchomieniu,
 * dzięki czemu konto z jawnym hasłem laboratoryjnym nie może przypadkowo stać
 * się mechanizmem bootstrapu środowiska produkcyjnego.
 *
 * <p>Event nie jest seedowany tutaj. Tworzy go funkcja {@code setup()} k6 przez
 * prawdziwe HTTP i security. Eksperyment sprawdza więc także logowanie i kontrakt
 * tworzenia danych, lecz pomiar latency obejmuje już tylko gorący write path
 * rezerwacji.</p>
 */
@Component
@Profile("load-test")
public class LoadTestIdentitySeeder implements ApplicationRunner {
    private static final String ORGANIZATION_NAME = "k6 load-test organization";

    private final OrganizationRepository organizationRepository;
    private final AppUserRepository appUserRepository;
    private final PasswordHasher passwordHasher;
    private final String managerEmail;
    private final String managerPassword;

    public LoadTestIdentitySeeder(
            OrganizationRepository organizationRepository,
            AppUserRepository appUserRepository,
            PasswordHasher passwordHasher,
            @Value("${performance.load-test.manager-email:load-manager@example.test}") String managerEmail,
            @Value("${performance.load-test.manager-password:load-test-password}") String managerPassword
    ) {
        this.organizationRepository = organizationRepository;
        this.appUserRepository = appUserRepository;
        this.passwordHasher = passwordHasher;
        this.managerEmail = managerEmail;
        this.managerPassword = managerPassword;
    }

    /**
     * Operacja jest idempotentna po adresie e-mail. Ponowne uruchomienie procesu
     * nie zmienia hasła ani organizacji istniejącego użytkownika, więc restart
     * aplikacji podczas eksperymentu nie modyfikuje badanego datasetu.
     */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (appUserRepository.findByEmail(managerEmail).isPresent()) {
            return;
        }

        Organization organization = organizationRepository.findByName(ORGANIZATION_NAME)
                .orElseGet(() -> organizationRepository.save(new Organization(ORGANIZATION_NAME)));
        appUserRepository.save(new AppUser(
                organization,
                managerEmail,
                passwordHasher.encode(managerPassword),
                UserRole.EVENT_MANAGER
        ));
    }
}
