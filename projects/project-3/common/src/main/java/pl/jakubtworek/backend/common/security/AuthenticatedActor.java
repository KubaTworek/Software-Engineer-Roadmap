package pl.jakubtworek.backend.common.security;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/** Body i nagłówek userId nie są tożsamością. Tylko zweryfikowany sub identyfikuje caller. */
public final class AuthenticatedActor {
    private final boolean enabled;
    public AuthenticatedActor(boolean enabled) { this.enabled=enabled; }
    public String userId(String claimed) {
        if (!enabled) return claimed; // Jawny lokalny playground zachowuje dotychczasowy kontrakt.
        var jwt = authentication();
        if (claimed != null && !claimed.equals(jwt.getName())) forbidden();
        return jwt.getName();
    }
    public void requireOwner(String owner) {
        if (!enabled) return;
        var jwt = authentication();
        if (!jwt.getName().equals(owner) && jwt.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals("SCOPE_internal"))) forbidden();
    }
    private JwtAuthenticationToken authentication() {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof JwtAuthenticationToken jwt)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        if(jwt.getName()==null || jwt.getName().isBlank())throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return jwt;
    }
    private void forbidden() { throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Resource ownership mismatch"); }
}
