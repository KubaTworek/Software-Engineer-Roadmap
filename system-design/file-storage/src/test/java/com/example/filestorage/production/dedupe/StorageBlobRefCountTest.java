package com.example.filestorage.production.dedupe;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Fizyczna zawartosc moze zniknac dopiero po zwolnieniu ostatniej referencji. */
class StorageBlobRefCountTest {
    @Test
    void referenceCountNeverBecomesNegative() {
        StorageBlob blob = new StorageBlob("hash", "blobs/hash", 10, null);
        blob.incrementRefCount();

        assertThat(blob.decrementRefCount()).isEqualTo(1);
        assertThat(blob.decrementRefCount()).isZero();
        assertThatThrownBy(blob::decrementRefCount).isInstanceOf(IllegalStateException.class);
    }
}
