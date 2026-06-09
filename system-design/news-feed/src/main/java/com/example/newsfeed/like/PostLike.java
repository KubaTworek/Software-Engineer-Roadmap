package com.example.newsfeed.like;

import com.example.newsfeed.post.Post;
import com.example.newsfeed.user.User;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "post_likes")
public class PostLike {
    @EmbeddedId private PostLikeId id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @MapsId("postId") @JoinColumn(name = "post_id") private Post post;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @MapsId("userId") @JoinColumn(name = "user_id") private User user;
    @Column(nullable = false) private Instant createdAt;
    protected PostLike() {}
    public PostLike(PostLikeId id, Post post, User user, Instant createdAt) { this.id = id; this.post = post; this.user = user; this.createdAt = createdAt; }
    public PostLikeId getId() { return id; }
}
