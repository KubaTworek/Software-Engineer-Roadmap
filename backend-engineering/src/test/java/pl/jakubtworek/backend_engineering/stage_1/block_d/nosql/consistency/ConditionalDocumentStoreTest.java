package pl.jakubtworek.backend_engineering.stage_1.block_d.nosql.consistency;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConditionalDocumentStoreTest {

    @Test
    void shouldPreventAStaleWriterFromOverwritingANewerValue() {
        ConditionalDocumentStore<Integer> store = new ConditionalDocumentStore<>();
        VersionedValue<Integer> firstReader = store.create("stock:book", 10);
        VersionedValue<Integer> secondReader = store.find("stock:book").orElseThrow();

        var firstWrite = store.replaceIfVersion("stock:book", firstReader.version(), 9);
        var staleWrite = store.replaceIfVersion("stock:book", secondReader.version(), 8);

        assertThat(firstWrite.applied()).isTrue();
        assertThat(staleWrite.applied()).isFalse();
        assertThat(staleWrite.current()).contains(new VersionedValue<>(9, 2));
    }

    @Test
    void shouldAllowTheClientToRetryFromTheLatestVersion() {
        ConditionalDocumentStore<Integer> store = new ConditionalDocumentStore<>();
        store.create("stock:book", 10);
        store.replaceIfVersion("stock:book", 1, 9);

        VersionedValue<Integer> latest = store.find("stock:book").orElseThrow();
        var retry = store.replaceIfVersion("stock:book", latest.version(), latest.value() - 1);

        assertThat(retry.applied()).isTrue();
        assertThat(retry.current()).contains(new VersionedValue<>(8, 3));
    }

    @Test
    void shouldNotSilentlyReplaceAnExistingDocumentDuringCreate() {
        ConditionalDocumentStore<String> store = new ConditionalDocumentStore<>();
        store.create("profile:1", "first");

        assertThatThrownBy(() -> store.create("profile:1", "second"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already exists");
    }
}
