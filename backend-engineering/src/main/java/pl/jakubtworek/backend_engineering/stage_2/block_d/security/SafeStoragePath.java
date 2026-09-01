package pl.jakubtworek.backend_engineering.stage_2.block_d.security;

import java.nio.file.Path;
import java.text.Normalizer;
import java.util.Set;

/** Resolves a client filename to one file directly below a configured storage root. */
public final class SafeStoragePath {

    private final Path root;
    private final Set<String> allowedExtensions;

    public SafeStoragePath(Path root, Set<String> allowedExtensions) {
        this.root = root.toAbsolutePath().normalize();
        this.allowedExtensions = Set.copyOf(allowedExtensions);
        if (this.allowedExtensions.isEmpty()) throw new IllegalArgumentException("allowedExtensions must not be empty");
    }

    public Path resolve(String clientFileName) {
        if (clientFileName == null || clientFileName.isBlank()) throw rejected("filename is required");
        String normalized = Normalizer.normalize(clientFileName, Normalizer.Form.NFKC);
        if (!normalized.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")) throw rejected("unsafe filename characters");
        if (normalized.equals(".") || normalized.equals("..") || normalized.contains("..")) {
            throw rejected("path traversal sequence");
        }
        int dot = normalized.lastIndexOf('.');
        if (dot < 1 || !allowedExtensions.contains(normalized.substring(dot + 1).toLowerCase())) {
            throw rejected("file extension is not allowed");
        }
        Path result = root.resolve(normalized).normalize();
        if (!result.getParent().equals(root)) throw rejected("file escaped storage root");
        return result;
    }

    private static UnsafePathException rejected(String reason) {
        return new UnsafePathException(reason);
    }

    public static final class UnsafePathException extends RuntimeException {
        public UnsafePathException(String message) {
            super(message);
        }
    }
}
