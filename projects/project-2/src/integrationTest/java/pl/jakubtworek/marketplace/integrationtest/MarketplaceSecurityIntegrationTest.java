package pl.jakubtworek.marketplace.integrationtest;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

/** Negatywny test systemowy: authentication, admin scope i object ownership. */
@ActiveProfiles({"postgres", "kafka", "secure"})
@TestPropertySource(properties = "marketplace.security.jwt-secret=integration-test-secret-with-at-least-32-characters")
class MarketplaceSecurityIntegrationTest extends AbstractIntegrationTest {
    @Test void signedTokenWithoutSubjectOrExpirationIsRejectedAtHttpBoundary() throws Exception {
        for(String missing:List.of("sub","exp")) {
            var original=SignedJWT.parse(token(UUID.randomUUID(),"admin"));
            var claims=new JWTClaimsSet.Builder(original.getJWTClaimsSet()).claim(missing,null).build();
            var jwt=new SignedJWT(new JWSHeader(JWSAlgorithm.HS256),claims);jwt.sign(new MACSigner(SECRET));
            assertThat(rest.exchange(url("/admin/kafka/dlq"),HttpMethod.GET,new HttpEntity<>(bearer(jwt.serialize())),Map.class)
                    .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }
    private static final String SECRET = "integration-test-secret-with-at-least-32-characters";

    @Test void rejectsAnonymousAndIdorButAllowsOwnerAndAdmin() throws Exception {
        assertThat(rest.getForEntity(url("/api/orders/" + UUID.randomUUID()), Map.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        UUID owner = UUID.randomUUID();
        HttpHeaders admin = bearer(token(UUID.randomUUID(), "admin"));
        ResponseEntity<Map> product = rest.exchange(url("/api/products"), HttpMethod.POST,
                new HttpEntity<>(Map.of("name", "Secure product", "amount", "12.00", "currency", "PLN"), admin), Map.class);
        assertThat(product.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID productId = UUID.fromString(product.getBody().get("id").toString());

        ResponseEntity<Map> placed = rest.exchange(url("/api/orders"), HttpMethod.POST,
                new HttpEntity<>(Map.of("customerId", owner.toString(), "lines",
                        List.of(Map.of("productId", productId.toString(), "quantity", 1))), admin), Map.class);
        UUID orderId = UUID.fromString(placed.getBody().get("id").toString());

        assertThat(rest.exchange(url("/api/orders/" + orderId), HttpMethod.GET,
                new HttpEntity<>(bearer(token(owner, ""))), Map.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(rest.exchange(url("/api/orders/" + orderId), HttpMethod.GET,
                new HttpEntity<>(bearer(token(UUID.randomUUID(), ""))), Map.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(rest.exchange(url("/admin/kafka/dlq"), HttpMethod.GET,
                new HttpEntity<>(bearer(token(owner, ""))), Map.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(rest.exchange(url("/admin/kafka/dlq"), HttpMethod.GET,
                new HttpEntity<>(admin), List.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test void rejectsCorrectlySignedTokensWithWrongOrMissingTrustClaims() throws Exception {
        for (String[] claims : new String[][]{{"https://untrusted.local", "marketplace-api"},
                {"https://identity.marketplace.local", "other-api"}, {null, "marketplace-api"},
                {"https://identity.marketplace.local", null}}) {
            assertThat(rest.exchange(url("/admin/kafka/dlq"), HttpMethod.GET,
                    new HttpEntity<>(bearer(token(UUID.randomUUID(), "admin", claims[0], claims[1]))), Map.class)
                    .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }
    private static HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders(); headers.setBearerAuth(token); return headers;
    }
    private static String token(UUID customerId, String scope) throws Exception {
        return token(customerId, scope, "https://identity.marketplace.local", "marketplace-api");
    }
    private static String token(UUID customerId, String scope, String issuer, String audience) throws Exception {
        Instant now = Instant.now();
        var claims = new JWTClaimsSet.Builder().subject(customerId.toString())
                .issuer(issuer).audience(audience).claim("customer_id", customerId.toString()).claim("scope", scope)
                .issueTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(300))).build();
        var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(SECRET));
        return jwt.serialize();
    }
}
