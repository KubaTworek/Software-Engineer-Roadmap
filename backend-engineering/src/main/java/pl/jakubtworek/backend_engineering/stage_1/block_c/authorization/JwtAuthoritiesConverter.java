package pl.jakubtworek.backend_engineering.stage_1.block_c.authorization;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

/**
 * Converts JWT claims into Spring Security authorities.
 *
 * Example JWT claims:
 *
 * {
 *   "sub": "john",
 *   "roles": ["ADMIN"],
 *   "permissions": ["ORDER_READ", "ORDER_WRITE"]
 * }
 *
 * Spring Security expects authorities like:
 * - ROLE_ADMIN
 * - ORDER_READ
 * - ORDER_WRITE
 */
@Component
public class JwtAuthoritiesConverter
        implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {

        Collection<SimpleGrantedAuthority> authorities =
                Stream.concat(
                        extractRoles(jwt).stream(),
                        extractPermissions(jwt).stream()
                ).toList();

        /**
         * JwtAuthenticationToken represents authenticated user
         * based on validated JWT.
         */
        return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    }

    private List<SimpleGrantedAuthority> extractRoles(Jwt jwt) {

        List<String> roles = jwt.getClaimAsStringList("roles");

        if (roles == null) {
            return List.of();
        }

        /**
         * hasRole("ADMIN") expects authority ROLE_ADMIN.
         */
        return roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
    }

    private List<SimpleGrantedAuthority> extractPermissions(Jwt jwt) {

        List<String> permissions = jwt.getClaimAsStringList("permissions");

        if (permissions == null) {
            return List.of();
        }

        /**
         * hasAuthority("ORDER_READ") expects exact authority ORDER_READ.
         */
        return permissions.stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
    }
}