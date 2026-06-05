package com.example.videostreaming.storage;

import io.minio.*;
import io.minio.http.Method;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

@Service
public class ObjectStorageService {
    private final MinioClient minio;
    private final StorageProperties props;

    public ObjectStorageService(MinioClient minio, StorageProperties props) {
        this.minio = minio;
        this.props = props;
    }

    public String presignedPutUrl(String objectKey) throws Exception {
        return minio.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                .method(Method.PUT)
                .bucket(props.bucket())
                .object(objectKey)
                .expiry(props.presignedUploadExpiryMinutes(), TimeUnit.MINUTES)
                .build());
    }

    public String presignedGetUrl(String objectKey, int minutes) throws Exception {
        return minio.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                .method(Method.GET)
                .bucket(props.bucket())
                .object(objectKey)
                .expiry(minutes, TimeUnit.MINUTES)
                .build());
    }

    public void download(String objectKey, Path target) throws Exception {
        Files.createDirectories(target.getParent());
        try (InputStream in = minio.getObject(GetObjectArgs.builder().bucket(props.bucket()).object(objectKey).build())) {
            Files.copy(in, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public void uploadFile(String objectKey, Path source, String contentType) throws Exception {
        minio.uploadObject(UploadObjectArgs.builder()
                .bucket(props.bucket())
                .object(objectKey)
                .filename(source.toString())
                .contentType(contentType)
                .build());
    }

    public String cdnUrl(String objectKey) {
        return props.cdnBaseUrl().replaceAll("/$", "") + "/" + objectKey;
    }
}
