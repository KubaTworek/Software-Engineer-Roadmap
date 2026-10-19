package pl.jakubtworek.backend_engineering.stage_2.block_d.security;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;

/** Validates filename, claimed media type, size and a minimal file signature. */
public final class UploadPolicy {

    private static final byte[] PNG = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] PDF = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final Map<String, Signature> ALLOWED = Map.of(
            "image/png", new Signature("png", PNG),
            "application/pdf", new Signature("pdf", PDF));

    private final int maximumBytes;
    private final SafeStoragePath storagePath;

    public UploadPolicy(Path storageRoot, int maximumBytes) {
        if (maximumBytes < 1) throw new IllegalArgumentException("maximumBytes must be positive");
        this.maximumBytes = maximumBytes;
        this.storagePath = new SafeStoragePath(storageRoot, Set.of("png", "pdf"));
    }

    public AcceptedUpload validate(String fileName, String claimedMediaType, byte[] content) {
        Path safePath = storagePath.resolve(fileName);
        if (content == null || content.length == 0 || content.length > maximumBytes) {
            throw new UploadRejectedException("file size is invalid");
        }
        Signature signature = ALLOWED.get(claimedMediaType);
        if (signature == null || !fileName.toLowerCase().endsWith("." + signature.extension())) {
            throw new UploadRejectedException("media type and extension are not allowed");
        }
        if (!startsWith(content, signature.prefix())) throw new UploadRejectedException("file signature does not match media type");
        return new AcceptedUpload(safePath, claimedMediaType, sha256(content), content.length);
    }

    private static boolean startsWith(byte[] content, byte[] prefix) {
        if (content.length < prefix.length) return false;
        for (int index = 0; index < prefix.length; index++) {
            if (content[index] != prefix[index]) return false;
        }
        return true;
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the JDK", exception);
        }
    }

    private record Signature(String extension, byte[] prefix) {
    }

    public record AcceptedUpload(Path path, String mediaType, String sha256, int bytes) {
    }

    public static final class UploadRejectedException extends RuntimeException {
        public UploadRejectedException(String message) {
            super(message);
        }
    }
}
