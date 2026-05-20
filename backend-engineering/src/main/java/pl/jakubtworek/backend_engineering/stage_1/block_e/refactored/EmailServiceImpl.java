package pl.jakubtworek.backend_engineering.stage_1.block_e.refactored;

import org.springframework.stereotype.Component;

@Component
public class EmailServiceImpl implements EmailServicePort {
    @Override
    public void sendWelcomeEmail(String email) {
        System.out.println("welcome email -> " + email);
    }
}
