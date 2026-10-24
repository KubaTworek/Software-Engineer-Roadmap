package pl.jakubtworek.marketplace.integrationtest;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.*;
import org.springframework.http.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** OIDC discovery i własność PII przez prawdziwy endpoint marketplace. */
@ActiveProfiles({"postgres","kafka","secure"})
@TestPropertySource(properties={"marketplace.security.jwt-mode=oidc","marketplace.security.jwt-audience=ticketing-api"})
class OidcContactOwnershipIntegrationTest extends AbstractIntegrationTest {
    private static final GenericContainer<?> IDP=new GenericContainer<>("quay.io/keycloak/keycloak:26.6.2")
            .withExposedPorts(8080).withCopyFileToContainer(MountableFile.forClasspathResource("identity/roadmap-realm.json"),"/opt/keycloak/data/import/roadmap-realm.json")
            .withCommand("start-dev","--import-realm").waitingFor(Wait.forHttp("/realms/roadmap/.well-known/openid-configuration").forStatusCode(200));
    static { IDP.start(); }
    @DynamicPropertySource static void identity(DynamicPropertyRegistry r){r.add("marketplace.security.jwt-issuer",()->"http://"+IDP.getHost()+":"+IDP.getMappedPort(8080)+"/realms/roadmap");}
    @Test void ownerCanManageContactButCannotClaimOtherCustomersIdentity() throws Exception {
        var client=HttpClient.newHttpClient();
        String issuer="http://"+IDP.getHost()+":"+IDP.getMappedPort(8080)+"/realms/roadmap";
        var reply=client.send(HttpRequest.newBuilder(URI.create(issuer+"/protocol/openid-connect/token"))
                .header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString("grant_type=password&client_id=roadmap-user&username=alice&password=local-alice-password")).build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(200,reply.statusCode());
        String token=new tools.jackson.databind.ObjectMapper().readTree(reply.body()).path("access_token").asString();
        HttpHeaders headers=new HttpHeaders();headers.setBearerAuth(token);
        String owner="11111111-1111-1111-1111-111111111aaa",other="22222222-2222-2222-2222-222222222bbb";
        var body=Map.of("email","alice@example.test","displayName","Alice");
        assertEquals(HttpStatus.OK,rest.exchange(url("/api/contacts/"+owner),HttpMethod.PUT,new HttpEntity<>(body,headers),String.class).getStatusCode());
        assertEquals(HttpStatus.OK,rest.exchange(url("/api/contacts/"+owner),HttpMethod.GET,new HttpEntity<>(headers),String.class).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN,rest.exchange(url("/api/contacts/"+other),HttpMethod.PUT,new HttpEntity<>(body,headers),String.class).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED,rest.getForEntity(url("/api/contacts/"+owner),String.class).getStatusCode());
        headers.set("Deletion-Request-Id",UUID.randomUUID().toString());
        assertEquals(HttpStatus.OK,rest.exchange(url("/api/contacts/"+owner),HttpMethod.DELETE,new HttpEntity<>(headers),String.class).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND,rest.exchange(url("/api/contacts/"+owner),HttpMethod.GET,new HttpEntity<>(headers),String.class).getStatusCode());
    }
}
