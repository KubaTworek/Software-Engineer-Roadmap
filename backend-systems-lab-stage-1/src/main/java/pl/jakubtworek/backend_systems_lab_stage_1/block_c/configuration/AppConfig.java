package pl.jakubtworek.backend_systems_lab_stage_1.block_c.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Java-based Spring configuration.
 *
 * Modern Spring applications prefer Java Config
 * instead of XML configuration.
 *
 * @Configuration marks class as source of bean definitions.
 *
 * proxyBeanMethods = false:
 * - disables CGLIB proxy for configuration class,
 * - improves startup performance,
 * - recommended when @Bean methods do not call each other.
 */
@Configuration(proxyBeanMethods = false)
public class AppConfig {

    /**
     * Bean manually registered in Spring context.
     *
     * Dependencies are resolved automatically
     * from Spring container.
     */
    @Bean
    public UserService userService(
            UserRepository userRepository,
            EmailService emailService
    ) {
        return new UserService(userRepository, emailService);
    }
}