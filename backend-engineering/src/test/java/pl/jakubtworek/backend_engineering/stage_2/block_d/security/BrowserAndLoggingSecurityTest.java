package pl.jakubtworek.backend_engineering.stage_2.block_d.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class BrowserAndLoggingSecurityTest {

    private final BrowserSecurityPolicy policy = new BrowserSecurityPolicy();

    @Test
    void cookieAuthenticationNeedsCsrfWhileBearerHeaderDoesNot() {
        BrowserSecurityPolicy.Decision cookie = policy.authorize(new BrowserSecurityPolicy.RequestContext(
                "POST", true, false, false, BrowserSecurityPolicy.CredentialTransport.COOKIE, false));
        BrowserSecurityPolicy.Decision bearer = policy.authorize(new BrowserSecurityPolicy.RequestContext(
                "POST", true, false, false, BrowserSecurityPolicy.CredentialTransport.AUTHORIZATION_HEADER, false));

        assertThat(cookie.allowed()).isFalse();
        assertThat(cookie.reason()).contains("CSRF");
        assertThat(bearer.allowed()).isTrue();
    }

    @Test
    void corsDenialAndSecurityHeadersAreIndependentFromAuthentication() {
        BrowserSecurityPolicy.Decision decision = policy.authorize(new BrowserSecurityPolicy.RequestContext(
                "GET", true, true, false, BrowserSecurityPolicy.CredentialTransport.AUTHORIZATION_HEADER, false));

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).contains("CORS");
        assertThat(policy.responseHeaders(true)).containsEntry("Cache-Control", "no-store")
                .containsKeys("Strict-Transport-Security", "Content-Security-Policy", "X-Content-Type-Options");
    }

    @Test
    void structuredLoggingRedactsPiiSecretsAndMisclassifiedAuthorizationHeader() {
        SecurityLogSanitizer sanitizer = new SecurityLogSanitizer();

        Map<String, String> result = sanitizer.sanitize(Map.of(
                "route", new SecurityLogSanitizer.LogField("/orders/{id}", SecurityLogSanitizer.Classification.PUBLIC),
                "user_id", new SecurityLogSanitizer.LogField("user-123", SecurityLogSanitizer.Classification.STABLE_IDENTIFIER),
                "email", new SecurityLogSanitizer.LogField("alice@example.com", SecurityLogSanitizer.Classification.PII),
                "Authorization", new SecurityLogSanitizer.LogField("Bearer stolen", SecurityLogSanitizer.Classification.PUBLIC)));

        assertThat(result.get("route")).isEqualTo("/orders/{id}");
        assertThat(result.get("user_id")).startsWith("id:").doesNotContain("user-123");
        assertThat(result.get("email")).isEqualTo("[REDACTED_PII]");
        assertThat(result.get("Authorization")).isEqualTo("[REDACTED_SECRET]");
        assertThat(result.toString()).doesNotContain("alice@example.com", "Bearer stolen");
    }
}
