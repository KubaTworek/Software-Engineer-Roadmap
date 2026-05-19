package pl.jakubtworek.backend_systems_lab_stage_1.block_e.legacy;

import org.springframework.stereotype.Service;

@Service
public class LegacyEmailService {
    public void sendWelcome(String email) {
        System.out.println("legacy welcome email -> " + email);
    }
}
