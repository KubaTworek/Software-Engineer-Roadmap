package com.example.paymentsystem.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Chroni przed BOLA/IDOR. Merchant identity pochodzi z uwierzytelnionego
 * principalu, a nie z kontrolowanego przez klienta pola requestu. Operatorzy
 * są jawną granicą administracyjną i mogą pracować między tenantami.
 */
@Component
public class MerchantAccessPolicy {
    public void requireAccess(Authentication authentication, UUID merchantId) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Authentication is required");
        }
        boolean operator = authentication.getAuthorities().stream().anyMatch(authority ->
                authority.getAuthority().equals("ROLE_ADMIN") || authority.getAuthority().equals("ROLE_OPERATOR"));
        if (operator) return;
        String expectedPrincipal = "merchant:" + merchantId;
        if (!expectedPrincipal.equals(authentication.getName())) {
            throw new AccessDeniedException("Merchant cannot access another tenant");
        }
    }
}
