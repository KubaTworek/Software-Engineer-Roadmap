package pl.jakubtworek.backend.common.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import java.time.Duration;
import java.util.List;

@Configuration(proxyBeanMethods=false)
public class TicketingSecurityConfiguration {
    @Bean AuthenticatedActor authenticatedActor(@Value("${app.security.enabled:false}") boolean enabled) { return new AuthenticatedActor(enabled); }
    @Bean SecurityFilterChain ticketingSecurity(HttpSecurity http, @Value("${app.security.enabled:false}") boolean enabled) throws Exception {
        http.csrf(csrf -> csrf.disable()).sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        if (!enabled) return http.authorizeHttpRequests(a -> a.anyRequest().permitAll()).build();
        return http.authorizeHttpRequests(a -> a
                .requestMatchers("/actuator/health/**").permitAll()
                .requestMatchers("/actuator/**").hasAuthority("SCOPE_ops")
                .requestMatchers("/payments/**","/internal/**","/chaos/**","/inventory/**","/reservations/*/confirm").hasAuthority("SCOPE_internal")
                .requestMatchers(HttpMethod.GET,"/events/**").permitAll()
                .anyRequest().authenticated()).oauth2ResourceServer(o -> o.jwt(j -> {})).build();
    }
    @Bean @ConditionalOnProperty(name="app.security.enabled",havingValue="true")
    JwtDecoder ticketingDecoder(@Value("${app.security.issuer}") String issuer,
            @Value("${app.security.jwks-uri}") String jwks, @Value("${app.security.audience:ticketing-api}") String audience) {
        var request = new SimpleClientHttpRequestFactory();
        request.setConnectTimeout(Duration.ofMillis(400)); request.setReadTimeout(Duration.ofMillis(400));
        var decoder=NimbusJwtDecoder.withJwkSetUri(jwks).restOperations(new RestTemplate(request)).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer),
                new JwtClaimValidator<List<String>>("aud", values -> values != null && values.contains(audience)),
                new JwtClaimValidator<java.time.Instant>("exp", value -> value != null),
                new JwtClaimValidator<String>("sub", value -> value != null && !value.isBlank())));
        return decoder;
    }
}
