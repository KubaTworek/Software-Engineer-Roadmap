package pl.jakubtworek.backend.common.security;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.test.context.*;
import org.springframework.web.bind.annotation.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.utility.MountableFile;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

/** Prawdziwy IdP i HTTP: podpis, ownership, rotacja JWKS oraz ciepły/zimny cache podczas awarii. */
@Tag("infrastructure") @Testcontainers
@SpringBootTest(classes=KeycloakIdentityInfrastructureTest.Config.class,webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties="app.security.enabled=true")
class KeycloakIdentityInfrastructureTest {
    @Container static GenericContainer<?> keycloak=new GenericContainer<>("quay.io/keycloak/keycloak:26.6.2")
            .withExposedPorts(8080).withEnv("KC_BOOTSTRAP_ADMIN_USERNAME","admin")
            .withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD","local-admin-password")
            .withCopyFileToContainer(MountableFile.forClasspathResource("identity/roadmap-realm.json"),"/opt/keycloak/data/import/roadmap-realm.json")
            .withCommand("start-dev","--import-realm").waitingFor(Wait.forHttp("/realms/roadmap/.well-known/openid-configuration").forStatusCode(200))
            .withStartupTimeout(Duration.ofMinutes(3));
    @DynamicPropertySource static void identity(DynamicPropertyRegistry r) {
        r.add("app.security.issuer",KeycloakIdentityInfrastructureTest::issuer);
        r.add("app.security.jwks-uri",() -> issuer()+"/protocol/openid-connect/certs");
    }
    static String issuer(){return "http://"+keycloak.getHost()+":"+keycloak.getMappedPort(8080)+"/realms/roadmap";}
    @Configuration(proxyBeanMethods=false) @EnableAutoConfiguration @Import({TicketingSecurityConfiguration.class,Probe.class}) static class Config {}
    @RestController static class Probe {
        private final AuthenticatedActor actor;
        Probe(AuthenticatedActor actor){this.actor=actor;}
        @GetMapping("/probe/{owner}") String owned(@PathVariable String owner){actor.requireOwner(owner);return "OK";}
        @PostMapping("/probe") String claim(@RequestBody String owner){return actor.userId(owner);}
        @GetMapping("/internal/probe") String service(){actor.requireOwner("another-owner");return "OK";}
    }
    @LocalServerPort int port;
    @Autowired org.springframework.security.oauth2.jwt.JwtDecoder decoder;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final ObjectMapper json=new ObjectMapper();
    @Test @Timeout(90) void validatesRealIssuerAndOwnershipThenRefreshesKeysWithoutAcceptingUnknownKeysOffline() throws Exception {
        String token=userToken("alice");
        var original=decoder.decode(token);
        assertEquals(401,call(null,"11111111-1111-1111-1111-111111111aaa").statusCode());
        assertEquals(200,call(token,original.getSubject()).statusCode());
        assertEquals(403,call(token,"22222222-2222-2222-2222-222222222bbb").statusCode());
        var forged=http.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/probe"))
                .header("Authorization","Bearer "+token).header("Content-Type","text/plain")
                .POST(HttpRequest.BodyPublishers.ofString("another-owner")).build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(403,forged.statusCode());
        var supplier=new ServiceTokenProvider(issuer()+"/protocol/openid-connect/token","roadmap-service","local-service-secret-not-for-production");
        String service=supplier.token().get(5,java.util.concurrent.TimeUnit.SECONDS);
        assertTrue(decoder.decode(service).getClaimAsString("scope").contains("internal"));
        assertEquals(200,internal(service));assertEquals(403,internal(token));
        var wrongIssuer=new TicketingSecurityConfiguration().ticketingDecoder(issuer()+"-other",issuer()+"/protocol/openid-connect/certs","ticketing-api");
        assertThrows(org.springframework.security.oauth2.jwt.JwtException.class,()->wrongIssuer.decode(token));
        var wrongAudience=new TicketingSecurityConfiguration().ticketingDecoder(issuer(),issuer()+"/protocol/openid-connect/certs","other-api");
        assertThrows(org.springframework.security.oauth2.jwt.JwtException.class,()->wrongAudience.decode(token));
        String discovery=http.send(HttpRequest.newBuilder(URI.create(issuer()+"/.well-known/openid-configuration")).GET().build(),HttpResponse.BodyHandlers.ofString()).body();
        assertEquals(issuer(),json.readTree(discovery).path("issuer").asString());
        String admin=json.readTree(form(issuer().replace("/realms/roadmap","/realms/master")+"/protocol/openid-connect/token",
                "grant_type=password&client_id=admin-cli&username=admin&password=local-admin-password")).path("access_token").asString();
        String component="""
                {"name":"rotated","parentId":"roadmap","providerId":"rsa-generated","providerType":"org.keycloak.keys.KeyProvider",
                 "config":{"priority":["200"],"enabled":["true"],"active":["true"],"algorithm":["RS256"],"keySize":["2048"]}}
                """;
        var rotation=http.send(HttpRequest.newBuilder(URI.create(issuer().replace("/realms/roadmap","/admin/realms/roadmap/components")))
                .header("Authorization","Bearer "+admin).header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofString(component)).build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(201,rotation.statusCode());
        String newer=userToken("alice");
        var rotated=decoder.decode(newer);
        assertNotEquals(original.getHeaders().get("kid"),rotated.getHeaders().get("kid"));
        assertEquals(original.getSubject(),rotated.getSubject());
        keycloak.getDockerClient().pauseContainerCmd(keycloak.getContainerId()).exec();
        try {
            // Znany klucz nadal w cache. Zimny decoder nie może zaakceptować tokenu bez kluczy.
            assertEquals(200,call(newer,original.getSubject()).statusCode());
            assertEquals(200,internal(supplier.token().get()));
            var cold=new TicketingSecurityConfiguration().ticketingDecoder(issuer(),issuer()+"/protocol/openid-connect/certs","ticketing-api");
            assertTimeoutPreemptively(Duration.ofSeconds(4),() -> assertThrows(org.springframework.security.oauth2.jwt.JwtException.class,() -> cold.decode(newer)));
        } finally {keycloak.getDockerClient().unpauseContainerCmd(keycloak.getContainerId()).exec();}
    }
    private String userToken(String user)throws Exception{return json.readTree(form(issuer()+"/protocol/openid-connect/token",
            "grant_type=password&client_id=roadmap-user&username="+user+"&password=local-"+user+"-password")).path("access_token").asString();}
    private String form(String url,String body)throws Exception{
        var response=http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5))
                .header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(200,response.statusCode());return response.body();
    }
    private HttpResponse<String> call(String token,String owner)throws Exception{
        var request=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/probe/"+owner)).timeout(Duration.ofSeconds(5));
        if(token!=null)request.header("Authorization","Bearer "+token);
        return http.send(request.GET().build(),HttpResponse.BodyHandlers.ofString());
    }
    private int internal(String token)throws Exception{return http.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/internal/probe"))
            .header("Authorization","Bearer "+token).GET().build(),HttpResponse.BodyHandlers.ofString()).statusCode();}
}
