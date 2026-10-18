package com.example.ratelimiter.security;

import static org.assertj.core.api.Assertions.assertThat;
import com.example.ratelimiter.config.RateLimiterProperties;
import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AdminAuthenticationFilterTest {
    @Test
    void adminEndpointFailsClosedWithoutToken() throws ServletException, IOException {
        RateLimiterProperties properties = new RateLimiterProperties();
        properties.getSecurity().setAdminToken("rotated-secret");
        var response = new MockHttpServletResponse();

        new AdminAuthenticationFilter(properties).doFilter(
                request("/admin/rules", null), response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void previousTokenRemainsValidDuringRotation() throws ServletException, IOException {
        RateLimiterProperties properties = new RateLimiterProperties();
        properties.getSecurity().setAdminToken("new-secret");
        properties.getSecurity().setPreviousAdminTokens(java.util.List.of("old-secret"));
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        new AdminAuthenticationFilter(properties).doFilter(
                request("/admin/rules", "old-secret"), response, chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    private MockHttpServletRequest request(String path, String token) {
        var request = new MockHttpServletRequest("GET", path);
        if (token != null) request.addHeader("X-Admin-Token", token);
        return request;
    }
}
