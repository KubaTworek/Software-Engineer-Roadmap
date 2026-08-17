package com.example.filestorage.upload;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class UploadChunkId implements Serializable {
    private UUID uploadSessionId;
    private int chunkIndex;

    public UploadChunkId() {}

    public UploadChunkId(UUID uploadSessionId, int chunkIndex) {
        this.uploadSessionId = uploadSessionId;
        this.chunkIndex = chunkIndex;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UploadChunkId that)) return false;
        return chunkIndex == that.chunkIndex && Objects.equals(uploadSessionId, that.uploadSessionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uploadSessionId, chunkIndex);
    }
}
