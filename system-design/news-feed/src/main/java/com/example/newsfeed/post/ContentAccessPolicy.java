package com.example.newsfeed.post;

import com.example.newsfeed.follow.FollowRepository;
import com.example.newsfeed.user.User;
import com.example.newsfeed.user.UserBlockRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Ostatnia, silnie egzekwowana granica prywatności. Projekcja feedu może być
 * spóźniona, ale przed zwróceniem treści source of truth jest sprawdzane ponownie.
 */
@Service
public class ContentAccessPolicy {
    private final FollowRepository follows;
    private final UserBlockRepository blocks;

    public ContentAccessPolicy(FollowRepository follows, UserBlockRepository blocks) {
        this.follows = follows;
        this.blocks = blocks;
    }

    public boolean canView(User viewer, Post post) {
        if (post.getStatus() != PostStatus.PUBLISHED || post.getDeletedAt() != null) return false;
        UUID authorId = post.getAuthor().getId();
        if (viewer == null) return post.getVisibility() == PostVisibility.PUBLIC;
        if (blocks.existsInEitherDirection(viewer.getId(), authorId)) return false;
        if (viewer.getId().equals(authorId)) return true;
        return switch (post.getVisibility()) {
            case PUBLIC -> true;
            case FOLLOWERS -> follows.existsByIdFollowerIdAndIdFolloweeId(viewer.getId(), authorId);
            case PRIVATE -> false;
        };
    }
}
