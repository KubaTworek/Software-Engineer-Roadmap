package com.example.urlshortener.admin;

import com.example.urlshortener.exception.AdminUnauthorizedException;
import org.springframework.stereotype.Service;
import com.example.urlshortener.security.SecretSupport;

@Service
public class AdminAuthService {
    private final AdminProperties properties;

    public AdminAuthService(AdminProperties properties) {
        this.properties = properties;
    }

    public void requireAdmin(String token) {
        String expected = properties.token();
        boolean matches = SecretSupport.constantTimeEquals(token, expected);
        for (String previous : properties.previousTokens()) matches |= SecretSupport.constantTimeEquals(token, previous);
        if (expected == null || expected.isBlank() || !matches) {
            throw new AdminUnauthorizedException();
        }
    }
}
