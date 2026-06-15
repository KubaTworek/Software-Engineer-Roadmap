package com.example.filestorage.production.kms;

import org.springframework.stereotype.Service;

import javax.crypto.KeyGenerator;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Lokalny serwis KMS używany do generowania data key.
 *
 * W produkcyjnej architekturze KMS zwykle oznacza zewnętrzny system:
 * - AWS KMS,
 * - GCP Cloud KMS,
 * - Azure Key Vault,
 * - HashiCorp Vault.
 *
 * Ta implementacja jest uproszczona i nadaje się głównie do local/dev/stage.
 * Generuje losowy klucz AES-256 i zwraca go jako plaintext w Base64.
 *
 * Uwaga bezpieczeństwa:
 * prawdziwy KMS zwykle zwraca:
 * - plaintext data key do jednorazowego użycia w aplikacji,
 * - encrypted data key do zapisania obok zaszyfrowanego obiektu.
 *
 * Tutaj encrypted data key nie istnieje, więc to nie jest pełny envelope encryption.
 */
@Service
public class KmsService {

    /**
     * Konfiguracja lokalnego KMS.
     *
     * Zawiera masterKey, z którego w tej implementacji wyliczany jest fingerprint
     * używany tylko do stworzenia keyId.
     *
     * Ten masterKey nie szyfruje tutaj data key.
     * Jest używany wyłącznie jako materiał do identyfikatora.
     */
    private final KmsProperties properties;

    public KmsService(KmsProperties properties) {
        this.properties = properties;
    }

    /**
     * Generuje nowy data key dla podanego celu użycia.
     *
     * keyPurpose:
     * logiczny opis przeznaczenia klucza, np. "file-content", "thumbnail", "backup".
     *
     * Działanie:
     * - generuje losowy klucz AES-256,
     * - liczy fingerprint masterKey,
     * - buduje keyId z keyPurpose i fingerprintu,
     * - zwraca plaintext key w Base64.
     *
     * Ten data key może być później użyty np. do szyfrowania zawartości pliku
     * przed zapisem w object storage.
     */
    public DataKey generateDataKey(String keyPurpose) {
        try {
            /*
             * Tworzymy generator kluczy AES.
             */
            KeyGenerator generator = KeyGenerator.getInstance("AES");

            /*
             * 256-bitowy klucz AES.
             *
             * Wymaga poprawnego wsparcia kryptograficznego w runtime Javy,
             * co we współczesnych wersjach JVM jest standardem.
             */
            generator.init(256);

            /*
             * Plaintext data key.
             *
             * To jest właściwy sekret używany do szyfrowania danych.
             * Nie powinien być logowany ani zapisywany jawnie w bazie.
             */
            byte[] plain = generator.generateKey().getEncoded();

            /*
             * Fingerprint masterKey.
             *
             * W tej implementacji służy tylko do zbudowania identyfikatora keyId.
             * Nie jest używany do szyfrowania wygenerowanego data key.
             */
            byte[] masterFingerprint = MessageDigest
                    .getInstance("SHA-256")
                    .digest(properties.masterKey().getBytes(java.nio.charset.StandardCharsets.UTF_8));

            /*
             * keyId identyfikuje logicznie, z jakiego "master key" pochodzi data key.
             *
             * Bierzemy tylko pierwsze 16 znaków hasha, żeby ID było krótkie.
             * To fingerprint, nie sekret.
             */
            String keyId = keyPurpose
                    + ":"
                    + HexFormat.of()
                    .formatHex(masterFingerprint)
                    .substring(0, 16);

            return new DataKey(
                    keyId,
                    Base64.getEncoder().encodeToString(plain)
            );
        } catch (Exception e) {
            /*
             * Fail-fast.
             * Jeśli nie da się wygenerować klucza, nie wolno kontynuować szyfrowania.
             */
            throw new IllegalStateException("Could not generate local development data key", e);
        }
    }

    /**
     * Wynik wygenerowania data key.
     *
     * keyId:
     * identyfikator klucza/master key używany do metadanych.
     *
     * base64PlaintextKey:
     * jawny data key zakodowany w Base64.
     *
     * Uwaga:
     * w produkcji plaintext key powinien żyć krótko w pamięci procesu
     * i nie powinien być zapisywany w logach, bazie ani response API.
     */
    public record DataKey(String keyId, String base64PlaintextKey) {}
}