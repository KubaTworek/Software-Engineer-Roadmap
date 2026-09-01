package pl.jakubtworek.backend_engineering.stage_2.block_d.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;

class UploadBoundaryTest {

    @Test
    void filenameMustResolveDirectlyBelowStorageRoot() {
        Path root = Path.of("build", "uploads").toAbsolutePath().normalize();
        SafeStoragePath paths = new SafeStoragePath(root, Set.of("png", "pdf"));

        assertThat(paths.resolve("invoice-42.pdf")).isEqualTo(root.resolve("invoice-42.pdf"));
        assertThatThrownBy(() -> paths.resolve("../secret.pdf"))
                .isInstanceOf(SafeStoragePath.UnsafePathException.class);
        assertThatThrownBy(() -> paths.resolve("..\\secret.pdf"))
                .isInstanceOf(SafeStoragePath.UnsafePathException.class);
        assertThatThrownBy(() -> paths.resolve("avatar.php"))
                .isInstanceOf(SafeStoragePath.UnsafePathException.class);
    }

    @Test
    void claimedContentTypeDoesNotOverrideFileSignature() {
        UploadPolicy policy = new UploadPolicy(Path.of("build", "uploads"), 1_024);
        byte[] executable = "#!/bin/sh\necho owned".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> policy.validate("avatar.png", "image/png", executable))
                .isInstanceOf(UploadPolicy.UploadRejectedException.class)
                .hasMessageContaining("signature");
    }

    @Test
    void validPngGetsDigestAndSafePath() {
        UploadPolicy policy = new UploadPolicy(Path.of("build", "uploads"), 1_024);
        byte[] png = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2};

        UploadPolicy.AcceptedUpload accepted = policy.validate("avatar.png", "image/png", png);

        assertThat(accepted.bytes()).isEqualTo(10);
        assertThat(accepted.sha256()).hasSize(64);
        assertThat(accepted.path().getFileName().toString()).isEqualTo("avatar.png");
    }

    @Test
    void streamingLimitMustProtectChunkedBodyEvenWithoutContentLength() throws Exception {
        BoundedBodyReader reader = new BoundedBodyReader();

        assertThatThrownBy(() -> reader.read(new ByteArrayInputStream(new byte[11]), -1, 10))
                .isInstanceOf(BoundedBodyReader.PayloadTooLargeException.class);
        assertThatThrownBy(() -> reader.read(new ByteArrayInputStream(new byte[1]), 100, 10))
                .isInstanceOf(BoundedBodyReader.PayloadTooLargeException.class);
    }
}
