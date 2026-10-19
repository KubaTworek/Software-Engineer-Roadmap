package pl.jakubtworek.chatsystem.media;

import io.minio.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import java.io.*;

/** Prawdziwy adapter S3-compatible. Quarantine nie jest dostępne dla downloadera. */
@Component
@Profile("minio")
public class MinioObjectStorage implements ObjectStorage {
    private final MinioClient client; private final String quarantine; private final String clean;
    public MinioObjectStorage(@Value("${app.media.minio.endpoint}") String endpoint,
            @Value("${app.media.minio.access-key}") String accessKey,
            @Value("${app.media.minio.secret-key}") String secretKey,
            @Value("${app.media.minio.quarantine-bucket:chat-quarantine}") String quarantine,
            @Value("${app.media.minio.clean-bucket:chat-clean}") String clean) {
        this.client = MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build();
        this.quarantine = quarantine; this.clean = clean;
        try { ensureBucket(quarantine); ensureBucket(clean); }
        catch (Exception ex) { throw new IllegalStateException("Cannot initialize MinIO buckets", ex); }
    }
    @Override public void putQuarantine(String key, byte[] content, String type) throws IOException {
        try (var in = new ByteArrayInputStream(content)) {
            client.putObject(PutObjectArgs.builder().bucket(quarantine).object(key)
                    .stream(in, content.length, -1).contentType(type).build());
        } catch (Exception ex) { throw io(ex); }
    }
    @Override public byte[] readQuarantine(String key) throws IOException {
        try (var in = client.getObject(GetObjectArgs.builder().bucket(quarantine).object(key).build())) {
            return in.readAllBytes();
        } catch (Exception ex) { throw io(ex); }
    }
    @Override public void promoteToClean(String key) throws IOException {
        try {
            client.copyObject(CopyObjectArgs.builder().bucket(clean).object(key)
                    .source(CopySource.builder().bucket(quarantine).object(key).build()).build());
            deleteQuarantine(key);
        } catch (Exception ex) { throw io(ex); }
    }
    @Override public byte[] readClean(String key) throws IOException {
        try (var in = client.getObject(GetObjectArgs.builder().bucket(clean).object(key).build())) {
            return in.readAllBytes();
        } catch (Exception ex) { throw io(ex); }
    }
    @Override public void deleteQuarantine(String key) throws IOException { remove(quarantine, key); }
    @Override public void deleteClean(String key) throws IOException { remove(clean, key); }
    private void remove(String bucket, String key) throws IOException {
        try { client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build()); }
        catch (Exception ex) { throw io(ex); }
    }
    private void ensureBucket(String bucket) throws Exception {
        if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build()))
            client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
    }
    private IOException io(Exception ex) { return ex instanceof IOException value ? value : new IOException(ex); }
}
