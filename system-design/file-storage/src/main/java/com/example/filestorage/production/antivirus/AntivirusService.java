package com.example.filestorage.production.antivirus;

import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Serwis odpowiedzialny za skanowanie antywirusowe plików.
 *
 * W aplikacji File Storage jest używany przez background worker,
 * najczęściej po zakończeniu uploadu pliku.
 *
 * Obsługiwane tryby:
 * - NOOP: tryb lokalny/dev, nic realnie nie skanuje,
 * - CLAMAV: wysyła strumień pliku do clamd przez socket.
 *
 * Ta klasa nie decyduje, co zrobić z zainfekowanym plikiem.
 * Zwraca tylko wynik skanu. Decyzja typu FAILED/QUARANTINED/BLOCKED
 * powinna być wykonana wyżej, np. w BackgroundProcessingWorker.
 */
@Service
public class AntivirusService {

    /**
     * Konfiguracja antywirusa.
     *
     * Zawiera m.in.:
     * - enabled,
     * - mode,
     * - clamdHost,
     * - clamdPort.
     */
    private final AntivirusProperties properties;

    public AntivirusService(AntivirusProperties properties) {
        this.properties = properties;
    }

    /**
     * Główna metoda skanowania pliku.
     *
     * Przyjmuje InputStream, czyli plik może być skanowany strumieniowo,
     * bez ładowania całej zawartości do pamięci.
     *
     * Zachowanie zależy od konfiguracji:
     * - jeśli antywirus jest wyłączony albo mode=NOOP, zwraca wynik clean,
     * - jeśli mode=CLAMAV, przekazuje stream do clamd,
     * - dla nieznanego trybu rzuca wyjątek.
     */
    public ScanResult scan(InputStream inputStream) {
        /*
         * Tryb lokalny/dev.
         *
         * Przydatny, żeby aplikacja działała bez uruchomionego ClamAV.
         * Nie zapewnia realnego bezpieczeństwa, więc nie powinien być używany produkcyjnie.
         */
        if (!properties.enabled()
                || properties.mode() == null
                || properties.mode().equalsIgnoreCase("NOOP")) {
            return new ScanResult(
                    true,
                    "NOOP scanner enabled for local development"
            );
        }

        /*
         * Produkcyjny tryb skanowania przez ClamAV daemon.
         */
        if (properties.mode().equalsIgnoreCase("CLAMAV")) {
            return scanWithClamAv(inputStream);
        }

        /*
         * Fail-fast dla błędnej konfiguracji.
         * Lepiej zatrzymać processing niż cicho pominąć skan.
         */
        throw new IllegalStateException("Unsupported antivirus mode: " + properties.mode());
    }

    /**
     * Skanuje plik przez ClamAV daemon.
     *
     * Używa protokołu INSTREAM:
     * - otwiera socket do clamd,
     * - wysyła komendę zINSTREAM,
     * - przesyła plik w blokach,
     * - kończy transmisję blokiem długości 0,
     * - czyta odpowiedź clamd.
     *
     * Dzięki temu plik nie musi być zapisywany lokalnie na dysku aplikacji.
     */
    private ScanResult scanWithClamAv(InputStream inputStream) {
        try (Socket socket = new Socket(properties.clamdHost(), properties.clamdPort())) {
            /*
             * Komenda ClamAV INSTREAM.
             * Prefix "z" oznacza wariant komendy zakończony bajtem null.
             */
            socket.getOutputStream().write(
                    "zINSTREAM\0".getBytes(StandardCharsets.US_ASCII)
            );

            byte[] buffer = new byte[8192];
            int read;

            /*
             * Plik jest czytany porcjami.
             * Każda porcja musi być poprzedzona 4-bajtową długością w big-endian.
             */
            while ((read = inputStream.read(buffer)) != -1) {
                socket.getOutputStream().write(new byte[] {
                        (byte) ((read >> 24) & 0xff),
                        (byte) ((read >> 16) & 0xff),
                        (byte) ((read >> 8) & 0xff),
                        (byte) (read & 0xff)
                });

                socket.getOutputStream().write(buffer, 0, read);
            }

            /*
             * Blok długości 0 kończy stream dla clamd.
             */
            socket.getOutputStream().write(new byte[] {0, 0, 0, 0});
            socket.getOutputStream().flush();

            /*
             * Odpowiedź ClamAV zawiera zwykle "OK" dla czystego pliku
             * albo nazwę wykrytego zagrożenia.
             */
            String response = new String(
                    socket.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8
            );

            /*
             * Prosta interpretacja wyniku.
             * Jeśli odpowiedź zawiera OK, traktujemy plik jako czysty.
             */
            boolean clean = response.contains("OK");

            return new ScanResult(
                    clean,
                    response.trim()
            );
        } catch (Exception e) {
            /*
             * Błąd skanowania jest traktowany jako błąd processingu.
             * Nie powinniśmy cicho oznaczać pliku jako clean, jeśli skan się nie udał.
             */
            throw new IllegalStateException("Antivirus scan failed", e);
        }
    }

    /**
     * Wynik skanowania antywirusowego.
     *
     * clean:
     * true, jeśli plik uznano za bezpieczny.
     *
     * details:
     * techniczny opis wyniku, np. odpowiedź ClamAV albo informacja o trybie NOOP.
     */
    public record ScanResult(boolean clean, String details) {}
}