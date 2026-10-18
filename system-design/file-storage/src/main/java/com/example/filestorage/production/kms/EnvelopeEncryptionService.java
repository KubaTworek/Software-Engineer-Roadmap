package com.example.filestorage.production.kms;

import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/** Wykonywalny model envelope encryption dla malych buforow edukacyjnych. */
@Service
public class EnvelopeEncryptionService {
    private final KmsService kms;
    private final SecureRandom random = new SecureRandom();

    public EnvelopeEncryptionService(KmsService kms) { this.kms = kms; }

    public EncryptedPayload encrypt(byte[] plaintext, String purpose) {
        try {
            var dataKey = kms.generateDataKey(purpose);
            byte[] iv = new byte[12];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(Base64.getDecoder().decode(dataKey.base64PlaintextKey()), "AES"),
                    new GCMParameterSpec(128, iv));
            return new EncryptedPayload(concat(iv, cipher.doFinal(plaintext)),
                    dataKey.encryptedDataKey(), dataKey.keyId(), dataKey.keyVersion());
        } catch (Exception exception) {
            throw new IllegalStateException("Could not encrypt payload", exception);
        }
    }

    public byte[] decrypt(EncryptedPayload payload) {
        try {
            byte[] iv = Arrays.copyOfRange(payload.ciphertext(), 0, 12);
            byte[] encrypted = Arrays.copyOfRange(payload.ciphertext(), 12, payload.ciphertext().length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(kms.decryptDataKey(payload.encryptedDataKey()), "AES"),
                    new GCMParameterSpec(128, iv));
            return cipher.doFinal(encrypted);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not decrypt payload", exception);
        }
    }

    private byte[] concat(byte[] left, byte[] right) {
        byte[] result = Arrays.copyOf(left, left.length + right.length);
        System.arraycopy(right, 0, result, left.length, right.length);
        return result;
    }

    public record EncryptedPayload(byte[] ciphertext, String encryptedDataKey,
                                   String keyId, String keyVersion) {
        public EncryptedPayload withRotatedEnvelope(KmsService.RotatedDataKey rotated) {
            return new EncryptedPayload(ciphertext, rotated.encryptedDataKey(), keyId, rotated.keyVersion());
        }
    }
}
