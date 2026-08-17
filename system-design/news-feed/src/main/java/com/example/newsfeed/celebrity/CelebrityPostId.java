package com.example.newsfeed.celebrity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class CelebrityPostId implements Serializable {
    private UUID authorId;
    private UUID postId;

    public CelebrityPostId() {
    }

    public CelebrityPostId(UUID authorId, UUID postId) {
        this.authorId = authorId;
        this.postId = postId;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof CelebrityPostId that)) {
            return false;
        }
        return Objects.equals(authorId, that.authorId) && Objects.equals(postId, that.postId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(authorId, postId);
    }
}
