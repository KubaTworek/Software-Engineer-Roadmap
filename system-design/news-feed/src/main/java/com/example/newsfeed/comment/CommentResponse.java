package com.example.newsfeed.comment;
import java.time.Instant;
import java.util.UUID;
public record CommentResponse(UUID id, UUID postId, CommentAuthorResponse author, String content, Instant createdAt, Instant updatedAt) {
    public static CommentResponse from(Comment comment) { return new CommentResponse(comment.getId(), comment.getPost().getId(), CommentAuthorResponse.from(comment.getAuthor()), comment.getContent(), comment.getCreatedAt(), comment.getUpdatedAt()); }
}
