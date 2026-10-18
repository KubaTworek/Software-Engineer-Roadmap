package com.example.videostreaming.drm;

import com.example.videostreaming.auth.User;
import com.example.videostreaming.auth.UserRole;
import com.example.videostreaming.catalog.Video;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class SignedCookieServiceTest {
    private static final String SECRET = "test-secret-with-at-least-thirty-two-characters";

    @Test
    void rejectsMalformedTamperedAndExpiredTokensWithoutThrowing() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        User user = new User("viewer@example.com", "hash", UserRole.USER);
        Video video = new Video("title", "description", user);
        SignedCookieService issuer = service(now);
        String token = issuer.createPlaybackToken(user, video, Duration.ofMinutes(5));

        assertThat(issuer.verifyPlaybackToken(token, user.getId().toString(), video.getId().toString())).isTrue();
        assertThat(issuer.verifyPlaybackToken("%%%not-base64.signature", user.getId().toString(), video.getId().toString())).isFalse();
        assertThat(issuer.verifyPlaybackToken(token + "tampered", user.getId().toString(), video.getId().toString())).isFalse();
        assertThat(service(now.plus(Duration.ofMinutes(6)))
                .verifyPlaybackToken(token, user.getId().toString(), video.getId().toString())).isFalse();
    }

    private SignedCookieService service(Instant instant) {
        return new SignedCookieService(SECRET, "", true, Clock.fixed(instant, ZoneOffset.UTC));
    }
}
