package com.example.filestorage.production.cdn;

import com.example.filestorage.storage.StorageService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/** Podpisany URL musi byc egzekwowalny kryptograficznie, a nie tylko opisany TTL-em. */
class CdnServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void signsPathAndExpiryWithStableHmac() {
        var properties = new CdnProperties(true, "https://cdn.example.test/", 300,
                "0123456789abcdef0123456789abcdef");
        var service = new CdnService(properties, mock(StorageService.class), CLOCK);

        DownloadUrlResponse response = service.signedDownloadUrl("users/u/file.pdf");

        assertThat(response.url())
                .startsWith("https://cdn.example.test/users/u/file.pdf?expires=1767225900&signature=")
                .doesNotContain("0123456789abcdef");
        assertThat(response.expiresAt()).isEqualTo(Instant.parse("2026-01-01T00:05:00Z"));
    }

    @Test
    void refusesWeakCdnSecret() {
        var service = new CdnService(new CdnProperties(true, "https://cdn.example.test", 300, "weak"),
                mock(StorageService.class), CLOCK);

        assertThatThrownBy(() -> service.signedDownloadUrl("object"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32");
    }
}
