package pl.jakubtworek.backend_engineering.stage_1.block_e.refactored;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class RegistrationConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    RegisterUserUseCase registerUserUseCase(
            UserRepositoryPort userRepositoryPort,
            EmailServicePort emailServicePort,
            Clock clock) {
        return new RegisterUserUseCase(userRepositoryPort, emailServicePort, clock);
    }
}
