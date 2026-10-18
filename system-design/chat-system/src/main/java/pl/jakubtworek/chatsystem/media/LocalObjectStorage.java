package pl.jakubtworek.chatsystem.media;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.nio.file.*;

/** Lokalny adapter zachowuje ten sam przepływ quarantine -> scan -> clean co object storage. */
@Component
@Profile("!minio")
public class LocalObjectStorage implements ObjectStorage {
    private final Path root;
    public LocalObjectStorage(@Value("${app.media.local-dir:./data/uploads}") String root) { this.root = Path.of(root); }
    @Override public void putQuarantine(String key, byte[] content, String type) throws IOException {
        Path target = safe("quarantine", key); Files.createDirectories(target.getParent()); Files.write(target, content);
    }
    @Override public byte[] readQuarantine(String key) throws IOException { return Files.readAllBytes(safe("quarantine", key)); }
    @Override public void promoteToClean(String key) throws IOException {
        Path source = safe("quarantine", key); Path target = safe("clean", key);
        Files.createDirectories(target.getParent()); Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
    }
    @Override public byte[] readClean(String key) throws IOException { return Files.readAllBytes(safe("clean", key)); }
    @Override public void deleteQuarantine(String key) throws IOException { Files.deleteIfExists(safe("quarantine", key)); }
    @Override public void deleteClean(String key) throws IOException { Files.deleteIfExists(safe("clean", key)); }
    private Path safe(String zone, String key) throws IOException {
        Path base = root.resolve(zone).toAbsolutePath().normalize();
        Path resolved = base.resolve(key).normalize();
        if (!resolved.startsWith(base)) throw new IOException("Object key escapes storage root");
        return resolved;
    }
}
