package com.example.urlshortener.admin;

import com.example.urlshortener.exception.AdminUnauthorizedException;
import org.springframework.stereotype.Service;

@Service
public class AdminAuthService {
    private final AdminProperties properties;

    public AdminAuthService(AdminProperties properties) {
        this.properties = properties;
    }

    public void requireAdmin(String token) {
        String expected = properties.token();
        if (expected == null || expected.isBlank() || token == null || !expected.equals(token)) {
            throw new AdminUnauthorizedException();
        }
    }
}
