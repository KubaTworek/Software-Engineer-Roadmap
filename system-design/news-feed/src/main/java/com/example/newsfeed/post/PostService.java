package com.example.newsfeed.post;

import com.example.newsfeed.common.NotFoundException;
import com.example.newsfeed.common.UnauthorizedException;
import com.example.newsfeed.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class PostService {

    private final PostRepository postRepository;

    public PostService(PostRepository postRepository) {
        this.postRepository = postRepository;
    }

    @Transactional
    public PostResponse createPost(User author, CreatePostRequest request) {
        Instant now = Instant.now();
        Post post = new Post(
                UUID.randomUUID(),
                author,
                request.content().trim(),
                now,
                now,
                null
        );

        Post savedPost = postRepository.save(post);
        return PostResponse.from(savedPost);
    }

    @Transactional(readOnly = true)
    public PostResponse getPost(UUID id) {
        Post post = postRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new NotFoundException("Post not found."));

        return PostResponse.from(post);
    }

    @Transactional
    public void deletePost(User currentUser, UUID id) {
        Post post = postRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new NotFoundException("Post not found."));

        if (!post.getAuthor().getId().equals(currentUser.getId())) {
            throw new UnauthorizedException("You can delete only your own posts.");
        }

        post.softDelete();
    }
}
