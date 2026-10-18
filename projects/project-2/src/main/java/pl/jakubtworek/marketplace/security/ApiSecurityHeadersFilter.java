package pl.jakubtworek.marketplace.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

/**
 * Security headers ograniczają zachowanie klienta po otrzymaniu odpowiedzi. Nie są
 * substytutem auth, CORS ani CSRF: CORS mówi przeglądarce, które originy mogą czytać
 * odpowiedź, CSRF chroni sesyjne credentials, a autoryzacja decyduje, czy podmiot
 * może wykonać operację. API zwraca restrykcyjne wartości jako bezpieczny baseline.
 */
@Component
public class ApiSecurityHeadersFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
        chain.doFilter(request, response);
    }
}
