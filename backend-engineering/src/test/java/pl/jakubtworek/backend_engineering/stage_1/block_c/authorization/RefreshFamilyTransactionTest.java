package pl.jakubtworek.backend_engineering.stage_1.block_c.authorization;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.beans.factory.annotation.Autowired;
import java.time.Instant;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Rzeczywiste proxy i nowa transakcja: wyjątek odpowiedzi nie cofa revocation. */
@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:refresh_audit;DB_CLOSE_DELAY=-1", "spring.jpa.hibernate.ddl-auto=create-drop"})
@ActiveProfiles("dev")
class RefreshFamilyTransactionTest {
    @Autowired RefreshTokenService service;
    @Autowired RefreshTokenRepository repository;
    @Test void reuseCommitsFamilyRevocationDespiteRejectedRequest() throws Exception {
        String raw = "audit-raw-token";
        String hash = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256")
                .digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        String family = UUID.randomUUID().toString();
        var used = new RefreshToken(hash, "alice", family, Instant.now().plusSeconds(600));
        used.revoke("ROTATED");
        repository.saveAndFlush(used);
        repository.saveAndFlush(new RefreshToken("successor-"+family, "alice", family, Instant.now().plusSeconds(600)));
        assertThrows(RefreshTokenReuseException.class, () -> service.rotateRefreshToken(raw));
        var current = repository.findAllByFamilyId(family);
        assertEquals(0, current.stream().filter(token -> !token.isRevoked()).count());
    }
}
