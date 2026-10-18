package com.example.filestorage.production.kms;

import org.junit.jupiter.api.Test;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KmsServiceTest {
    @Test
    void encryptedDekCanBeUnwrappedButDoesNotExposePlaintext() {
        KmsService kms = new KmsService(new KmsProperties("master-key-version-one-32-characters"));
        var dataKey = kms.generateDataKey("file-content");

        assertThat(kms.decryptDataKey(dataKey.encryptedDataKey()))
                .isEqualTo(Base64.getDecoder().decode(dataKey.base64PlaintextKey()));
        assertThat(dataKey.encryptedDataKey()).doesNotContain(dataKey.base64PlaintextKey());
    }

    @Test
    void rotationRewrapsDekWithoutChangingIt() {
        String oldMaster = "old-master-key-material-32-characters";
        KmsService oldKms = new KmsService(new KmsProperties(oldMaster));
        var original = oldKms.generateDataKey("file-content");
        KmsService newKms = new KmsService(new KmsProperties("new-master-key-material-32-characters"));

        var rotated = newKms.rewrap(original.encryptedDataKey(), oldMaster);

        assertThat(newKms.decryptDataKey(rotated.encryptedDataKey()))
                .isEqualTo(Base64.getDecoder().decode(original.base64PlaintextKey()));
        assertThatThrownBy(() -> oldKms.decryptDataKey(rotated.encryptedDataKey()))
                .isInstanceOf(IllegalStateException.class);
    }
}
