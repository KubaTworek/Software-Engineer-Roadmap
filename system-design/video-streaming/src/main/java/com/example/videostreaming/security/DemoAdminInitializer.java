package com.example.videostreaming.security;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Konto demo można świadomie włączyć w lokalnym Compose. Nie jest już aktywnym
 * efektem ubocznym migracji uruchamianej na każdym środowisku.
 */
@Component
@ConditionalOnProperty(prefix = "app.demo-admin", name = "enabled", havingValue = "true")
public class DemoAdminInitializer implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final String password;

    public DemoAdminInitializer(JdbcTemplate jdbc, PasswordEncoder encoder,
                                @Value("${app.demo-admin.password}") String password) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (password == null || password.length() < 12 || "admin1234".equals(password)) {
            throw new IllegalStateException("Demo admin password must be changed and contain at least 12 characters");
        }
        jdbc.update("update users set password_hash = ?, status = 'ACTIVE', updated_at = now() where id = ?::uuid",
                encoder.encode(password), "00000000-0000-0000-0000-000000000001");
    }
}
