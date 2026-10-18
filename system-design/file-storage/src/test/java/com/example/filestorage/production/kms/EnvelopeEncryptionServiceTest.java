package com.example.filestorage.production.kms;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class EnvelopeEncryptionServiceTest {
    @Test
    void rotatingKekChangesOnlyDekEnvelopeNotLargeCiphertext() {
        String oldMaster = "old-master-key-material-32-characters";
        KmsService oldKms = new KmsService(new KmsProperties(oldMaster));
        var oldEncryption = new EnvelopeEncryptionService(oldKms);
        byte[] plaintext = "private file bytes".getBytes(StandardCharsets.UTF_8);
        var encrypted = oldEncryption.encrypt(plaintext, "file-content");

        KmsService newKms = new KmsService(new KmsProperties("new-master-key-material-32-characters"));
        var rotatedKey = newKms.rewrap(encrypted.encryptedDataKey(), oldMaster);
        var rotatedPayload = encrypted.withRotatedEnvelope(rotatedKey);

        assertThat(rotatedPayload.ciphertext()).isSameAs(encrypted.ciphertext());
        assertThat(rotatedPayload.encryptedDataKey()).isNotEqualTo(encrypted.encryptedDataKey());
        assertThat(new EnvelopeEncryptionService(newKms).decrypt(rotatedPayload)).isEqualTo(plaintext);
    }
}
