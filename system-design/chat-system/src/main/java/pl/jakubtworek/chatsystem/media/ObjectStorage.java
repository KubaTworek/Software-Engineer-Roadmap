package pl.jakubtworek.chatsystem.media;

import java.io.IOException;

/** Production boundary: quarantine i clean są odrębnymi strefami z różnymi ACL. */
public interface ObjectStorage {
    void putQuarantine(String key, byte[] content, String contentType) throws IOException;
    byte[] readQuarantine(String key) throws IOException;
    void promoteToClean(String key) throws IOException;
    byte[] readClean(String key) throws IOException;
    void deleteQuarantine(String key) throws IOException;
    void deleteClean(String key) throws IOException;
}
