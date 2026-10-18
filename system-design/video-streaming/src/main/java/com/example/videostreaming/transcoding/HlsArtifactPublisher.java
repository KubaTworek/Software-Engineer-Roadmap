package com.example.videostreaming.transcoding;

import com.example.videostreaming.storage.ObjectStorageService;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Publikuje gotową paczkę HLS zgodnie z najważniejszym niezmiennikiem systemu:
 * manifest widoczny dla playera nie może wskazywać nieistniejących assetów.
 *
 * Object storage nie zapewnia atomowego rename katalogu. Dlatego segmenty są
 * wysyłane pierwsze, potem playlisty wariantów, a master manifest jako ostatni
 * „commit marker”. Status READY można ustawić dopiero po sukcesie tej metody.
 */
@Component
public class HlsArtifactPublisher {
    private final ObjectStorageService storage;

    public HlsArtifactPublisher(ObjectStorageService storage) {
        this.storage = storage;
    }

    public String publishCompletePackage(Path packageDirectory, String finalPrefix) throws Exception {
        Path master = packageDirectory.resolve("master.m3u8");
        validatePackage(packageDirectory, master);

        List<Path> files;
        try (var walk = Files.walk(packageDirectory)) {
            files = walk.filter(Files::isRegularFile).toList();
        }

        // Segmenty muszą istnieć zanim którakolwiek playlista zacznie je reklamować.
        upload(files.stream().filter(this::isSegment).sorted().toList(), packageDirectory, finalPrefix);

        // Playlisty wariantów mogą zostać opublikowane po segmentach, ale przed masterem.
        upload(files.stream()
                .filter(this::isPlaylist)
                .filter(path -> !path.equals(master))
                .sorted()
                .toList(), packageDirectory, finalPrefix);

        upload(List.of(master), packageDirectory, finalPrefix);
        return finalPrefix + "/master.m3u8";
    }

    void validatePackage(Path root, Path master) throws IOException {
        requireNonEmpty(master, "master playlist");
        List<String> variants = mediaReferences(Files.readAllLines(master));
        if (variants.isEmpty()) {
            throw new IllegalStateException("HLS master playlist contains no variants");
        }

        for (String variantReference : variants) {
            Path variant = safeResolve(root, variantReference);
            requireNonEmpty(variant, "variant playlist " + variantReference);

            List<String> segments = mediaReferences(Files.readAllLines(variant));
            if (segments.isEmpty()) {
                throw new IllegalStateException("Variant playlist contains no segments: " + variantReference);
            }
            for (String segmentReference : segments) {
                Path segment = safeResolve(variant.getParent(), segmentReference);
                requireNonEmpty(segment, "segment " + segmentReference);
            }
        }
    }

    private List<String> mediaReferences(List<String> lines) {
        List<String> result = new ArrayList<>();
        for (String line : lines) {
            String value = line.trim();
            if (!value.isEmpty() && !value.startsWith("#")) {
                result.add(value);
            }
        }
        return result;
    }

    private Path safeResolve(Path root, String relativeReference) {
        Path resolved = root.resolve(relativeReference).normalize();
        if (!resolved.startsWith(root.normalize())) {
            throw new IllegalStateException("HLS manifest escapes package directory: " + relativeReference);
        }
        return resolved;
    }

    private void requireNonEmpty(Path file, String description) throws IOException {
        if (!Files.isRegularFile(file) || Files.size(file) == 0) {
            throw new IllegalStateException("Missing or empty " + description);
        }
    }

    private void upload(List<Path> files, Path root, String prefix) throws Exception {
        for (Path file : files) {
            String relative = root.relativize(file).toString().replace('\\', '/');
            storage.uploadFile(prefix + "/" + relative, file, contentType(file));
        }
    }

    private boolean isPlaylist(Path file) {
        return file.getFileName().toString().endsWith(".m3u8");
    }

    private boolean isSegment(Path file) {
        String name = file.getFileName().toString();
        return name.endsWith(".ts") || name.endsWith(".m4s");
    }

    private String contentType(Path file) {
        String name = file.getFileName().toString();
        if (name.endsWith(".m3u8")) return "application/vnd.apple.mpegurl";
        if (name.endsWith(".m4s")) return "video/iso.segment";
        return "video/mp2t";
    }
}
