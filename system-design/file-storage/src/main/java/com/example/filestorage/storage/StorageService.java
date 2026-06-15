package com.example.filestorage.storage;

import io.minio.*;
import io.minio.http.Method;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Serwis odpowiedzialny za fizyczną komunikację z object storage.
 *
 * W tej aplikacji metadane plików są trzymane w bazie danych,
 * ale właściwa zawartość plików trafia do MinIO/S3.
 *
 * Ta klasa ukrywa szczegóły MinIO SDK przed resztą aplikacji.
 * FileService, UploadService czy Workerzy nie muszą wiedzieć,
 * jak dokładnie wykonać putObject, getObject, copyObject albo composeObject.
 */
@Service
public class StorageService {

    /**
     * Klient MinIO SDK.
     * W produkcji ten sam wzorzec można wykorzystać dla S3-compatible storage.
     */
    private final MinioClient minioClient;

    /**
     * Konfiguracja storage, np. nazwa bucketa.
     * Dzięki temu bucket nie jest hardcodowany w metodach.
     */
    private final StorageProperties properties;

    public StorageService(MinioClient minioClient, StorageProperties properties) {
        this.minioClient = minioClient;
        this.properties = properties;
    }

    /**
     * Uploaduje plik otrzymany z klasycznego multipart/form-data.
     *
     * Ten wariant jest używany głównie dla małych plików.
     * Dla dużych plików lepszy jest upload przez presigned URL i chunki,
     * żeby backend nie musiał pośredniczyć w transferze całej zawartości.
     *
     * Metoda tylko otwiera InputStream z MultipartFile i deleguje właściwy zapis do put().
     */
    public void upload(String objectKey, MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            put(
                    objectKey,
                    inputStream,
                    file.getSize(),
                    file.getContentType() == null ? "application/octet-stream" : file.getContentType()
            );
        } catch (Exception e) {
            throw new IllegalStateException("Could not upload file to object storage", e);
        }
    }

    /**
     * Zapisuje dowolny strumień danych jako obiekt w storage.
     *
     * To bazowa metoda uploadu:
     * - używana przez upload multipart,
     * - może być używana przez workerów,
     * - może zapisywać finalny plik, thumbnail albo inny artefakt.
     *
     * objectKey to techniczna ścieżka obiektu w bucketcie.
     * Nie musi i nie powinna być zależna wyłącznie od nazwy pliku użytkownika.
     */
    public void put(String objectKey, InputStream inputStream, long sizeBytes, String contentType) {
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(properties.bucket())
                    .object(objectKey)

                    /*
                     * sizeBytes mówi MinIO, ile bajtów zostanie wysłanych.
                     * -1 jako partSize oznacza, że SDK samo dobiera strategię multipart,
                     * jeśli jest potrzebna.
                     */
                    .stream(inputStream, sizeBytes, -1)

                    /*
                     * Content-Type jest ważny przy downloadzie, podglądzie plików,
                     * CDN i generowaniu miniaturek.
                     */
                    .contentType(contentType == null ? "application/octet-stream" : contentType)
                    .build());
        } catch (Exception e) {
            throw new IllegalStateException("Could not put object to object storage", e);
        }
    }

    /**
     * Generuje tymczasowy URL do bezpośredniego uploadu obiektu przez klienta.
     *
     * To kluczowy mechanizm dla dużych plików:
     * - backend nie przesyła zawartości pliku przez siebie,
     * - klient wysyła chunk albo plik bezpośrednio do MinIO/S3,
     * - backend zachowuje kontrolę, bo URL jest ważny tylko przez określony czas
     *   i tylko dla konkretnego objectKey.
     *
     * Używane w flow chunked/resumable upload.
     */
    public String presignedPutUrl(String objectKey, Duration expiry) {
        try {
            int seconds = Math.toIntExact(expiry.toSeconds());

            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.PUT)
                    .bucket(properties.bucket())
                    .object(objectKey)
                    .expiry(seconds, TimeUnit.SECONDS)
                    .build());
        } catch (Exception e) {
            throw new IllegalStateException("Could not create presigned upload URL", e);
        }
    }

    /**
     * Generuje tymczasowy URL do bezpośredniego pobrania obiektu.
     *
     * To wariant przydatny dla CDN albo pobierania dużych plików:
     * - API sprawdza uprawnienia użytkownika,
     * - API zwraca krótko ważny URL,
     * - klient pobiera plik bezpośrednio ze storage/CDN.
     *
     * Dzięki temu backend nie musi streamować dużych plików przez własne instancje.
     */
    public String presignedGetUrl(String objectKey, Duration expiry) {
        try {
            int seconds = Math.toIntExact(expiry.toSeconds());

            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(properties.bucket())
                    .object(objectKey)
                    .expiry(seconds, TimeUnit.SECONDS)
                    .build());
        } catch (Exception e) {
            throw new IllegalStateException("Could not create presigned download URL", e);
        }
    }

    /**
     * Pobiera obiekt ze storage jako InputStream.
     *
     * Ten wariant jest używany, gdy backend sam streamuje plik do klienta,
     * np. przez endpoint /download.
     *
     * Przy bardzo dużych plikach bardziej skalowalne jest presignedGetUrl(),
     * ale stream przez backend daje większą kontrolę nad dostępem i nagłówkami.
     */
    public InputStream download(String objectKey) {
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(properties.bucket())
                    .object(objectKey)
                    .build());
        } catch (Exception e) {
            throw new IllegalStateException("Could not download file from object storage", e);
        }
    }

    /**
     * Pobiera rozmiar obiektu bez pobierania całej zawartości.
     *
     * Użyteczne po uploadzie przez presigned URL:
     * - backend może sprawdzić, czy chunk faktycznie istnieje,
     * - może zweryfikować rozmiar,
     * - może wykryć niepełny albo błędny upload.
     */
    public long size(String objectKey) {
        try {
            return minioClient.statObject(StatObjectArgs.builder()
                    .bucket(properties.bucket())
                    .object(objectKey)
                    .build()).size();
        } catch (Exception e) {
            throw new IllegalStateException("Could not stat object in object storage", e);
        }
    }

    /**
     * Składa wiele obiektów źródłowych w jeden finalny obiekt.
     *
     * To kluczowe dla chunked upload:
     * - każdy chunk jest wysyłany jako osobny obiekt,
     * - po zakończeniu uploadu backend składa chunki w jeden plik,
     * - metadane pliku wskazują później na targetObjectKey.
     *
     * sourceObjectKeys muszą być przekazane w poprawnej kolejności chunków.
     * Jeśli kolejność będzie błędna, finalny plik będzie uszkodzony.
     */
    public void compose(String targetObjectKey, List<String> sourceObjectKeys, String contentType) {
        try {
            List<ComposeSource> sources = sourceObjectKeys.stream()
                    .map(key -> ComposeSource.builder()
                            .bucket(properties.bucket())
                            .object(key)
                            .build())
                    .toList();

            minioClient.composeObject(ComposeObjectArgs.builder()
                    .bucket(properties.bucket())
                    .object(targetObjectKey)
                    .sources(sources)

                    /*
                     * Content-Type ustawiamy na finalnym obiekcie,
                     * żeby download/podgląd działały poprawnie.
                     */
                    .headers(java.util.Map.of(
                            "Content-Type",
                            contentType == null ? "application/octet-stream" : contentType
                    ))
                    .build());
        } catch (Exception e) {
            throw new IllegalStateException("Could not compose chunks in object storage", e);
        }
    }

    /**
     * Kopiuje obiekt w obrębie tego samego bucketa.
     *
     * Przydatne między innymi dla:
     * - tworzenia nowej wersji na bazie starego obiektu,
     * - przywracania wersji,
     * - deduplikacji,
     * - operacji technicznych bez pobierania pliku do backendu.
     *
     * Kopiowanie odbywa się po stronie storage, więc aplikacja nie musi streamować danych.
     */
    public void copy(String targetObjectKey, String sourceObjectKey, String contentType) {
        try {
            minioClient.copyObject(CopyObjectArgs.builder()
                    .bucket(properties.bucket())
                    .object(targetObjectKey)
                    .source(CopySource.builder()
                            .bucket(properties.bucket())
                            .object(sourceObjectKey)
                            .build())
                    .headers(java.util.Map.of(
                            "Content-Type",
                            contentType == null ? "application/octet-stream" : contentType
                    ))
                    .build());
        } catch (Exception e) {
            throw new IllegalStateException("Could not copy object in object storage", e);
        }
    }

    /**
     * Usuwa obiekt ze storage.
     *
     * Ta metoda powinna być wywoływana ostrożnie.
     *
     * Przy soft delete nie należy usuwać obiektu fizycznie,
     * bo plik może zostać przywrócony z kosza.
     *
     * Fizyczne delete ma sens przy:
     * - permanent delete,
     * - garbage collection,
     * - cleanupie niedokończonych uploadów,
     * - usuwaniu starych wersji zgodnie z retencją.
     */
    public void delete(String objectKey) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(properties.bucket())
                    .object(objectKey)
                    .build());
        } catch (Exception e) {
            throw new IllegalStateException("Could not delete file from object storage", e);
        }
    }
}