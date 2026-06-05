package com.example.newsfeed.post;

import com.example.newsfeed.auth.CurrentUser;
import com.example.newsfeed.user.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/posts")
public class PostController {

    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PostResponse createPost(
            @CurrentUser User currentUser,
            @Valid @RequestBody CreatePostRequest request
    ) {
        return postService.createPost(currentUser, request);
    }

    @GetMapping("/{id}")
    public PostResponse getPost(@PathVariable UUID id) {
        return postService.getPost(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePost(
            @CurrentUser User currentUser,
            @PathVariable UUID id
    ) {
        postService.deletePost(currentUser, id);
    }
}
