package pl.jakubtworek.marketplace.security;

import java.nio.file.Path;
import java.util.Set;

/**
 * Walidacja uploadu na granicy systemu. Nazwa klienta nigdy nie staje się ścieżką
 * docelową; serwer generuje własny identyfikator. Limit jest sprawdzany również
 * podczas streamowania, bo Content-Length może brakować albo kłamać. MIME z nagłówka
 * jest tylko deklaracją — adapter powinien dodatkowo wykryć typ po magic bytes.
 */
public record UploadPolicy(long maxBytes, Set<String> allowedMediaTypes) {
    public Path safeTarget(Path storageRoot, String generatedId, String declaredType, long streamedBytes) {
        if (streamedBytes < 0 || streamedBytes > maxBytes || !allowedMediaTypes.contains(declaredType)) {
            throw new SecurityException("upload rejected by size or media type policy");
        }
        Path normalizedRoot = storageRoot.toAbsolutePath().normalize();
        Path target = normalizedRoot.resolve(generatedId).normalize();
        if (!target.getParent().equals(normalizedRoot)) throw new SecurityException("path traversal rejected");
        return target;
    }
}
