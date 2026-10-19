package pl.jakubtworek.backend_engineering.stage_1.block_c.authorization;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.Clock;
import java.time.ZoneOffset;
import java.security.SecureRandom;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RefreshTokenServiceTest {

    private final RefreshTokenRepository repository = mock(RefreshTokenRepository.class);
    private final Instant now = Instant.parse("2026-08-31T10:00:00Z");
    private final RefreshTokenService service = new RefreshTokenService(
            repository,
            Clock.fixed(now, ZoneOffset.UTC),
            new SecureRandom()
    );

    @Test
    void shouldStoreOnlyHashAndReturnRawToken() {
        String rawToken = service.createRefreshToken("alice");

        ArgumentCaptor<RefreshToken> tokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(tokenCaptor.capture());

        assertThat(rawToken).isNotBlank();
        assertThat(tokenCaptor.getValue().getTokenHash()).isNotEqualTo(rawToken);
        assertThat(tokenCaptor.getValue().getUsername()).isEqualTo("alice");
    }

    @Test
    void shouldRevokeOldTokenAndPreserveItsOwnerDuringRotation() {
        RefreshToken oldToken = new RefreshToken(
                "stored-hash", "alice", "family-1", now.plusSeconds(60)
        );
        when(repository.findByTokenHash(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(Optional.of(oldToken));

        RotatedRefreshToken rotated = service.rotateRefreshToken("old-raw-token");

        assertThat(oldToken.isRevoked()).isTrue();
        assertThat(rotated.username()).isEqualTo("alice");
        assertThat(rotated.rawToken()).isNotBlank();
    }

    @Test
    void shouldRejectAlreadyRevokedToken() {
        RefreshToken oldToken = new RefreshToken(
                "stored-hash", "alice", "family-1", now.plusSeconds(60)
        );
        oldToken.revoke("ROTATED");
        RefreshToken successor = new RefreshToken(
                "successor-hash", "alice", "family-1", now.plusSeconds(60)
        );
        when(repository.findByTokenHash(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(Optional.of(oldToken));
        when(repository.findAllByFamilyId("family-1"))
                .thenReturn(List.of(oldToken, successor));

        assertThatThrownBy(() -> service.rotateRefreshToken("old-raw-token"))
                .isInstanceOf(RefreshTokenReuseException.class);
        assertThat(successor.isRevoked()).isTrue();
        assertThat(successor.getRevocationReason()).isEqualTo("REUSE_DETECTED");
    }

    @Test
    void shouldTreatTheExactExpirationBoundaryAsExpired() {
        RefreshToken expired = new RefreshToken(
                "stored-hash", "alice", "family-1", now
        );
        when(repository.findByTokenHash(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.rotateRefreshToken("old-raw-token"))
                .isInstanceOf(InvalidRefreshTokenException.class);
        assertThat(expired.getRevocationReason()).isEqualTo("EXPIRED");
    }
}
