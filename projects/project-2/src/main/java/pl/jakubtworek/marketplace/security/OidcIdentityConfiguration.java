package pl.jakubtworek.marketplace.security;

import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import java.time.*;
import java.util.List;

/** OIDC discovery wybiera JWKS z zaufanej konfiguracji issuera, nigdy z nagłówka tokenu. */
@Configuration(proxyBeanMethods=false) @Profile("secure")
@ConditionalOnProperty(name="marketplace.security.jwt-mode",havingValue="oidc")
public class OidcIdentityConfiguration {
    @Bean JwtDecoder oidcDecoder(@Value("${marketplace.security.jwt-issuer}") String issuer,
            @Value("${marketplace.security.jwt-audience}") String audience) {
        var request=new SimpleClientHttpRequestFactory();
        request.setConnectTimeout(Duration.ofMillis(500));request.setReadTimeout(Duration.ofMillis(500));
        var decoder=NimbusJwtDecoder.withIssuerLocation(issuer).restOperations(new RestTemplate(request)).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer),
                new JwtClaimValidator<List<String>>("aud", values -> values!=null && values.contains(audience)),
                new JwtClaimValidator<Instant>("exp", value -> value!=null),
                new JwtClaimValidator<String>("sub", value -> value!=null&&!value.isBlank())));
        return decoder;
    }
}
