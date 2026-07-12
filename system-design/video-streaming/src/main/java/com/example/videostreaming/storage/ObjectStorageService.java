package com.example.videostreaming.storage;

import io.minio.*;
import io.minio.http.Method;
import io.minio.messages.Item;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Serwis dostępu do object storage.
 *
 * W lokalnym środowisku używa MinIO, ale architektonicznie pełni rolę
 * abstrakcji nad storage typu S3.
 *
 * Główna odpowiedzialność:
 * - generowanie signed URL-i do uploadu i pobierania plików,
 * - pobieranie obiektów na lokalny dysk workera,
 * - upload gotowych assetów po transkodowaniu,
 * - listowanie plików pod danym prefixem,
 * - budowanie publicznego URL-a przez CDN.
 *
 * Ważne:
 * Ten serwis nie zna logiki biznesowej filmów.
 * Operuje wyłącznie na objectKey, bucketach i plikach.
 */
@Service
public class ObjectStorageService {

    /**
     * Klient MinIO/S3.
     *
     * W MVP wskazuje na lokalne MinIO.
     * W produkcji ta sama warstwa mogłaby zostać podmieniona
     * na AWS S3, GCS albo Azure Blob Storage.
     */
    private final MinioClient minio;

    /**
     * Konfiguracja storage.
     *
     * Zawiera m.in.:
     * - nazwę bucketu,
     * - czas ważności signed URL-a,
     * - bazowy URL CDN.
     */
    private final StorageProperties props;

    public ObjectStorageService(MinioClient minio, StorageProperties props) {
        this.minio = minio;
        this.props = props;
    }

    /**
     * Generuje tymczasowy signed URL do uploadu pliku.
     *
     * Używane w flow uploadu:
     * 1. Admin tworzy upload przez API.
     * 2. Backend zwraca signed PUT URL.
     * 3. Klient wysyła plik bezpośrednio do object storage.
     *
     * Dzięki temu backend nie przyjmuje wielogigabajtowych plików
     * i nie jest bottleneckiem dla uploadu.
     *
     * @param objectKey ścieżka obiektu w buckecie, np. raw/{videoId}/source.mp4
     * @return tymczasowy URL pozwalający wykonać HTTP PUT
     */
    public String presignedPutUrl(String objectKey) throws Exception {
        return minio.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                .method(Method.PUT)
                .bucket(props.bucket())
                .object(objectKey)
                .expiry(props.presignedUploadExpiryMinutes(), TimeUnit.MINUTES)
                .build());
    }

    /**
     * Generuje tymczasowy signed URL do pobrania obiektu.
     *
     * Używane tam, gdzie obiekt nie powinien być publiczny,
     * ale klient albo inny komponent potrzebuje czasowego dostępu.
     *
     * W kontekście video streaming częściej używamy CDN URL-i
     * dla gotowych segmentów HLS, ale signed GET URL jest przydatny
     * np. dla prywatnych plików, diagnostyki albo pobierania raw assetów.
     *
     * @param objectKey ścieżka obiektu w buckecie
     * @param minutes czas ważności linku
     * @return tymczasowy URL pozwalający wykonać HTTP GET
     */
    public String presignedGetUrl(String objectKey, int minutes) throws Exception {
        return minio.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                .method(Method.GET)
                .bucket(props.bucket())
                .object(objectKey)
                .expiry(minutes, TimeUnit.MINUTES)
                .build());
    }

    /**
     * Pobiera obiekt ze storage na lokalny dysk.
     *
     * Używane głównie przez worker transkodujący:
     * - worker odbiera job,
     * - pobiera raw video ze storage,
     * - uruchamia FFmpeg lokalnie,
     * - wysyła wynikowe segmenty HLS z powrotem do storage.
     *
     * Metoda tworzy katalog docelowy, jeśli jeszcze nie istnieje.
     *
     * @param objectKey ścieżka obiektu w buckecie
     * @param target lokalna ścieżka pliku docelowego
     */
    public void download(String objectKey, Path target) throws Exception {
        Files.createDirectories(target.getParent());

        try (InputStream in = minio.getObject(
                GetObjectArgs.builder()
                        .bucket(props.bucket())
                        .object(objectKey)
                        .build()
        )) {
            Files.copy(in, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Uploaduje lokalny plik do object storage.
     *
     * Używane po transkodowaniu:
     * - manifesty HLS,
     * - segmenty .ts / .m4s,
     * - miniatury,
     * - napisy,
     * - inne wygenerowane assety.
     *
     * contentType jest ważny, bo CDN i player powinny dostać poprawny typ pliku,
     * np. application/vnd.apple.mpegurl dla .m3u8 albo video/mp2t dla .ts.
     *
     * @param objectKey docelowa ścieżka w buckecie
     * @param source lokalna ścieżka pliku źródłowego
     * @param contentType MIME type zapisywanego obiektu
     */
    public void uploadFile(String objectKey, Path source, String contentType) throws Exception {
        minio.uploadObject(UploadObjectArgs.builder()
                .bucket(props.bucket())
                .object(objectKey)
                .filename(source.toString())
                .contentType(contentType)
                .build());
    }

    /**
     * Zwraca listę object key znajdujących się pod danym prefixem.
     *
     * Używane np. do:
     * - zebrania wszystkich segmentów HLS danego filmu,
     * - pre-warmingu CDN,
     * - diagnostyki assetów,
     * - operacji administracyjnych na paczce plików.
     *
     * recursive=true oznacza, że pobierane są też obiekty z podkatalogów.
     *
     * Uwaga:
     * Pojedyncze błędy odczytu elementu są ignorowane.
     * Dla MVP jest to akceptowalne, ale produkcyjnie lepiej logować takie przypadki.
     *
     * @param prefix prefix ścieżki, np. hls/{videoId}/
     * @return lista pełnych object key
     */
    public List<String> listObjectKeys(String prefix) {
        List<String> keys = new ArrayList<>();

        Iterable<Result<Item>> results = minio.listObjects(ListObjectsArgs.builder()
                .bucket(props.bucket())
                .prefix(prefix)
                .recursive(true)
                .build());

        for (Result<Item> result : results) {
            try {
                keys.add(result.get().objectName());
            } catch (Exception ignored) {
                // Pomijamy pojedynczy obiekt, którego metadanych nie udało się odczytać.
                // W produkcji warto dodać tu log warning/debug.
            }
        }

        return keys;
    }

    /**
     * Buduje publiczny URL do obiektu przez skonfigurowany CDN base URL.
     *
     * Używane przez playback API do zwrócenia klientowi adresu manifestu HLS
     * albo innych gotowych assetów.
     *
     * Przykład:
     * cdnBaseUrl = https://cdn.example.com
     * objectKey   = hls/video-123/master.m3u8
     * wynik       = https://cdn.example.com/hls/video-123/master.m3u8
     *
     * Ważne:
     * Ta metoda nie podpisuje URL-a i nie sprawdza uprawnień.
     * Kontrola dostępu powinna być wykonana wcześniej w PlaybackService,
     * np. przez entitlements, signed cookies albo signed CDN policy.
     */
    public String cdnUrl(String objectKey) {
        return props.cdnBaseUrl().replaceAll("/$", "") + "/" + objectKey;
    }
}