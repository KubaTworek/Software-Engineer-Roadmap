package pl.jakubtworek.backend_systems_lab_stage_1.block_c.configuration;

import org.springframework.stereotype.Service;

/**
 * Component automatically discovered by component scanning.
 *
 * @ComponentScan is enabled automatically
 * by @SpringBootApplication.
 */
@Service
public class EmailService {

    public void sendWelcomeEmail(String email) {

        System.out.println(
                "Sending email to: " + email
        );
    }
}