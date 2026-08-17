package com.example.filestorage.production.thumbnail;

import com.example.filestorage.file.FileMetadata;
import com.example.filestorage.storage.StorageService;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Locale;
import java.util.UUID;

/**
 * Serwis odpowiedzialny za generowanie miniaturek plików graficznych.
 *
 * Jest uruchamiany jako część background processingu po uploadzie pliku.
 *
 * Nie każdy plik dostaje miniaturkę.
 * Miniaturka jest generowana tylko wtedy, gdy:
 * - thumbnailing jest włączony w konfiguracji,
 * - plik ma contentType zaczynający się od "image/",
 * - ImageIO potrafi odczytać obraz.
 *
 * Wynikiem jest objectKey miniaturki zapisanej w object storage.
 */
@Service
public class ThumbnailService {

    /**
     * Konfiguracja generowania miniaturek.
     *
     * Zawiera m.in.:
     * - enabled,
     * - maxWidth,
     * - maxHeight.
     */
    private final ThumbnailProperties properties;

    /**
     * Serwis object storage.
     *
     * Używany do:
     * - pobrania oryginalnego obrazu,
     * - zapisania wygenerowanej miniaturki.
     */
    private final StorageService storageService;

    public ThumbnailService(ThumbnailProperties properties,
                            StorageService storageService) {
        this.properties = properties;
        this.storageService = storageService;
    }

    /**
     * Generuje miniaturkę, jeśli plik jest obsługiwanym obrazem.
     *
     * Zwraca:
     * - objectKey miniaturki, jeśli została wygenerowana,
     * - null, jeśli miniaturka nie powinna albo nie może zostać wygenerowana.
     *
     * Ta metoda nie zapisuje objectKey miniaturki w FileMetadata.
     * Zwraca go do workera/jobu, który może zapisać wynik processingu.
     */
    public String generateIfSupported(FileMetadata file) {
        /*
         * Szybkie odrzucenie przypadków, których nie obsługujemy:
         * - thumbnailing wyłączony,
         * - brak contentType,
         * - plik nie jest obrazem.
         */
        if (!properties.enabled()
                || file.getContentType() == null
                || !file.getContentType().toLowerCase(Locale.ROOT).startsWith("image/")) {
            return null;
        }

        try (InputStream inputStream = storageService.download(file.getObjectKey())) {
            /*
             * Odczyt obrazu z object storage.
             * ImageIO.read zwróci null, jeśli format nie jest obsługiwany
             * albo plik deklaruje image/*, ale faktycznie nie jest poprawnym obrazem.
             */
            BufferedImage source = ImageIO.read(inputStream);

            if (source == null) {
                return null;
            }

            /*
             * Wyliczamy rozmiar miniaturki z zachowaniem proporcji.
             *
             * Najpierw ograniczamy szerokość do maxWidth.
             * Jeśli wynikowa wysokość przekracza maxHeight,
             * przeliczamy rozmiar ponownie względem wysokości.
             */
            int targetWidth = Math.min(properties.maxWidth(), source.getWidth());

            int targetHeight = Math.max(
                    1,
                    (int) ((double) source.getHeight() * targetWidth / source.getWidth())
            );

            if (targetHeight > properties.maxHeight()) {
                targetHeight = properties.maxHeight();

                targetWidth = Math.max(
                        1,
                        (int) ((double) source.getWidth() * targetHeight / source.getHeight())
                );
            }

            /*
             * Tworzymy obraz wynikowy w RGB.
             *
             * JPEG nie obsługuje kanału alpha, więc TYPE_INT_RGB jest bezpiecznym wyborem
             * dla zapisu do formatu jpg.
             */
            BufferedImage thumbnail = new BufferedImage(
                    targetWidth,
                    targetHeight,
                    BufferedImage.TYPE_INT_RGB
            );

            /*
             * Skalowanie obrazu.
             * Bilinear interpolation daje rozsądny kompromis między jakością i kosztem.
             */
            Graphics2D g = thumbnail.createGraphics();

            g.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR
            );

            g.drawImage(
                    source,
                    0,
                    0,
                    targetWidth,
                    targetHeight,
                    null
            );

            /*
             * Graphics2D trzeba zwolnić, bo trzyma zasoby natywne.
             */
            g.dispose();

            /*
             * Miniaturkę zapisujemy do bufora jako JPEG.
             */
            ByteArrayOutputStream out = new ByteArrayOutputStream();

            ImageIO.write(
                    thumbnail,
                    "jpg",
                    out
            );

            byte[] bytes = out.toByteArray();

            /*
             * Miniaturka jest osobnym obiektem w storage.
             * Nie nadpisuje oryginalnego pliku.
             */
            String objectKey = "thumbnails/%s/%s.jpg".formatted(
                    file.getOwnerId(),
                    UUID.randomUUID()
            );

            storageService.put(
                    objectKey,
                    new ByteArrayInputStream(bytes),
                    bytes.length,
                    "image/jpeg"
            );

            return objectKey;
        } catch (Exception e) {
            /*
             * Błąd generowania miniaturki oznacza błąd joba processingu.
             * Nie powinien psuć samego uploadu pliku, bo działa w tle.
             */
            throw new IllegalStateException("Could not generate thumbnail", e);
        }
    }
}