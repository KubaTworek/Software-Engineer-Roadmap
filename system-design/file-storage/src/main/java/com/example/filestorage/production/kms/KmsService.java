package com.example.filestorage.production.kms;

import org.springframework.stereotype.Service;

import javax.crypto.KeyGenerator;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
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
 * Lokalny adapter realizuje semantyke envelope encryption: losowy DEK szyfruje
 * dane, a KEK pochodzacy z master key szyfruje DEK. Produkcyjnie operacje KEK
 * powinny zostac przeniesione do zewnetrznego KMS/HSM.
 */
@Service
public class KmsService {

    /**
     * Konfiguracja lokalnego KMS.
     *
     * Zawiera lokalny masterKey, z którego SHA-256 wyprowadza 256-bitowy KEK.
     * KEK opakowuje losowy data key; do metadanych trafia wyłącznie jego
     * wersja/fingerprint. To model dydaktyczny: produkcyjny KEK nie powinien
     * opuszczać KMS/HSM, a aplikacja powinna wywołać operację wrap/unwrap.
     */
    private final KmsProperties properties;
    private final SecureRandom random = new SecureRandom();

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
     * - wyprowadza KEK z lokalnego masterKey,
     * - opakowuje data key przez AES-GCM z losowym IV,
     * - buduje keyId z keyPurpose i wersji KEK,
     * - zwraca plaintext wyłącznie do natychmiastowego szyfrowania oraz
     *   encryptedDataKey bezpieczny do zapisania przy metadanych bloba.
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
             * Dydaktyczny KEK wyprowadzony z masterKey. W prawdziwym KMS ta
             * operacja zachodzi po stronie usługi, bez ujawniania KEK aplikacji.
             */
            byte[] kek = deriveKek(properties.masterKey());
            byte[] iv = new byte[12];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(kek, "AES"), new GCMParameterSpec(128, iv));
            byte[] wrapped = cipher.doFinal(plain);

            /*
             * keyId identyfikuje logicznie, z jakiego "master key" pochodzi data key.
             *
             * Bierzemy tylko pierwsze 16 znaków hasha, żeby ID było krótkie.
             * To fingerprint, nie sekret.
             */
            String keyId = keyPurpose
                    + ":"
                    + keyVersion(properties.masterKey());

            return new DataKey(
                    keyId,
                    Base64.getEncoder().encodeToString(plain),
                    Base64.getEncoder().encodeToString(concat(iv, wrapped)),
                    keyVersion(properties.masterKey())
            );
        } catch (Exception e) {
            /*
             * Fail-fast.
             * Jeśli nie da się wygenerować klucza, nie wolno kontynuować szyfrowania.
             */
            throw new IllegalStateException("Could not generate local development data key", e);
        }
    }

    public byte[] decryptDataKey(String encryptedDataKey) {
        return decryptDataKey(encryptedDataKey, properties.masterKey());
    }

    /**
     * Rotacja KEK nie wymaga ponownego szyfrowania wielkiego pliku. Odszyfrowuje
     * tylko maly DEK starym KEK i opakowuje go aktualnym KEK.
     */
    public RotatedDataKey rewrap(String encryptedDataKey, String previousMasterKey) {
        try {
            byte[] plain = decryptDataKey(encryptedDataKey, previousMasterKey);
            byte[] iv = new byte[12];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(deriveKek(properties.masterKey()), "AES"),
                    new GCMParameterSpec(128, iv));
            return new RotatedDataKey(
                    Base64.getEncoder().encodeToString(concat(iv, cipher.doFinal(plain))),
                    keyVersion(properties.masterKey()));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not rotate encrypted data key", exception);
        }
    }

    private byte[] decryptDataKey(String encryptedDataKey, String masterKey) {
        try {
            byte[] envelope = Base64.getDecoder().decode(encryptedDataKey);
            if (envelope.length <= 12) throw new IllegalArgumentException("Invalid encrypted data key");
            byte[] iv = java.util.Arrays.copyOfRange(envelope, 0, 12);
            byte[] ciphertext = java.util.Arrays.copyOfRange(envelope, 12, envelope.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(deriveKek(masterKey), "AES"),
                    new GCMParameterSpec(128, iv));
            return cipher.doFinal(ciphertext);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not decrypt data key", exception);
        }
    }

    private byte[] deriveKek(String masterKey) throws Exception {
        return MessageDigest.getInstance("SHA-256")
                .digest(masterKey.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private String keyVersion(String masterKey) throws Exception {
        return HexFormat.of().formatHex(deriveKek(masterKey)).substring(0, 16);
    }

    private byte[] concat(byte[] left, byte[] right) {
        byte[] result = java.util.Arrays.copyOf(left, left.length + right.length);
        System.arraycopy(right, 0, result, left.length, right.length);
        return result;
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
    public record DataKey(String keyId, String base64PlaintextKey,
                          String encryptedDataKey, String keyVersion) {}
    public record RotatedDataKey(String encryptedDataKey, String keyVersion) {}
}
