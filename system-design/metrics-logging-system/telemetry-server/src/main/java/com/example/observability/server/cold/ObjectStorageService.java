package com.example.observability.server.cold;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.List;
import java.util.zip.GZIPOutputStream;

/**
 * Lokalna implementacja object storage.
 *
 * W projekcie pełni rolę cold storage dla starszych danych telemetrycznych.
 * ColdExportJob zapisuje tutaj wyeksportowane logi i metryki jako pliki .ndjson.gz.
 *
 * To nie jest prawdziwy S3/GCS/Azure Blob klient.
 * To filesystem-backed adapter, który udaje object storage na potrzeby MVP.
 *
 * Dzięki tej abstrakcji reszta aplikacji nie musi wiedzieć,
 * czy dane są zapisywane lokalnie, do MinIO, S3, GCS czy Azure Blob.
 */
@Service
public class ObjectStorageService {

    /**
     * Katalog bazowy lokalnego object storage.
     *
     * Domyślnie:
     * /data/object-storage
     *
     * Konfiguracja:
     * telemetry.object-storage.local-root
     *
     * Wszystkie objectKey muszą finalnie znajdować się pod tym katalogiem.
     */
    private final Path root;

    public ObjectStorageService(
            @Value("${telemetry.object-storage.local-root:/data/object-storage}") String localRoot
    ) {
        this.root = Paths.get(localRoot);
    }

    /**
     * Zapisuje listę linii jako gzipowany obiekt.
     *
     * Używane przez ColdExportJob do zapisu:
     * - logs/yyyy/MM/dd/HH/logs.ndjson.gz
     * - metrics/yyyy/MM/dd/HH/metrics.ndjson.gz
     *
     * Każdy element listy lines jest jedną linią NDJSON.
     *
     * Przepływ:
     * 1. Zamienia objectKey na ścieżkę pod root.
     * 2. Normalizuje ścieżkę.
     * 3. Sprawdza, czy wynik nadal znajduje się pod root.
     * 4. Tworzy katalogi nadrzędne.
     * 5. Zapisuje linie przez GZIPOutputStream.
     * 6. Zwraca URI w formacie object://...
     */
    public String putGzipLines(String objectKey, List<String> lines) {
        try {
            /*
             * normalize() usuwa elementy typu "." i "..".
             *
             * Potem startsWith(root) chroni przed path traversal,
             * np. objectKey = ../../etc/passwd.
             */
            Path path = root.resolve(objectKey).normalize();

            if (!path.startsWith(root)) {
                throw new IllegalArgumentException("invalid object key");
            }

            /*
             * ColdExportJob zapisuje dane w strukturze katalogów po czasie,
             * więc katalogi mogą jeszcze nie istnieć.
             */
            Files.createDirectories(path.getParent());

            /*
             * CREATE + TRUNCATE_EXISTING oznacza zapis idempotentny na poziomie pliku:
             * ponowny eksport tego samego key nadpisze poprzedni obiekt.
             *
             * To jest proste dla MVP, ale produkcyjnie warto rozważyć:
             * - atomic write przez plik tymczasowy,
             * - checksums,
             * - manifest eksportu,
             * - blokadę przed przypadkowym nadpisaniem.
             */
            try (GZIPOutputStream gzip = new GZIPOutputStream(
                    Files.newOutputStream(
                            path,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.TRUNCATE_EXISTING
                    )
            )) {
                for (String line : lines) {
                    gzip.write(line.getBytes(StandardCharsets.UTF_8));
                    gzip.write('\n');
                }
            }

            /*
             * Zwracamy logiczne URI, nie lokalną ścieżkę filesystemu.
             *
             * Dzięki temu caller traktuje wynik jak obiekt storage'owy,
             * a nie jako plik lokalny.
             */
            return "object://" + objectKey;

        } catch (IOException e) {
            /*
             * IOException oznacza problem storage'u:
             * - brak uprawnień,
             * - brak miejsca,
             * - błąd filesystemu,
             * - problem z katalogiem.
             *
             * Zamieniamy na unchecked exception, żeby job eksportu mógł
             * potraktować zapis jako nieudany.
             */
            throw new IllegalStateException("object storage write failed", e);
        }
    }

    /**
     * Listuje obiekty znajdujące się pod podanym prefiksem.
     *
     * Używane przez AdminController:
     * GET /api/v1/admin/objects?prefix=...
     *
     * Przykłady prefixów:
     * - logs/
     * - metrics/
     * - logs/2026/06/16/
     *
     * Zwraca metadane:
     * - URI,
     * - rozmiar w bajtach,
     * - czas ostatniej modyfikacji.
     */
    public List<ObjectInfo> list(String prefix) {
        try {
            /*
             * Prefix jest traktowany jak katalog pod root.
             *
             * Tak samo jak przy zapisie, normalize + startsWith(root)
             * chroni przed wyjściem poza katalog object storage.
             */
            Path base = root
                    .resolve(prefix == null ? "" : prefix)
                    .normalize();

            if (!base.startsWith(root) || !Files.exists(base)) {
                return List.of();
            }

            /*
             * Files.walk przechodzi rekurencyjnie po katalogu.
             *
             * Dla MVP to wystarczy.
             * Produkcyjnie przy dużej liczbie obiektów potrzebna byłaby paginacja,
             * limit wyników i prawdziwe API listowania object storage.
             */
            try (var stream = Files.walk(base)) {
                return stream
                        .filter(Files::isRegularFile)
                        .map(p -> {
                            try {
                                return new ObjectInfo(
                                        "object://" + root.relativize(p).toString(),
                                        Files.size(p),
                                        Files.getLastModifiedTime(p).toInstant()
                                );
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        })
                        .toList();
            }

        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Metadane pojedynczego obiektu w cold storage.
     *
     * uri:
     * - logiczny identyfikator obiektu, np. object://logs/2026/06/16/12/logs.ndjson.gz
     *
     * bytes:
     * - rozmiar pliku po kompresji gzip.
     *
     * modifiedAt:
     * - timestamp ostatniej modyfikacji pliku.
     */
    public record ObjectInfo(
            String uri,
            long bytes,
            Instant modifiedAt
    ) {
    }
}