package com.example.newsfeed.feed;

import com.example.newsfeed.post.Post;
import com.example.newsfeed.post.PostRepository;
import com.example.newsfeed.post.PostResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class FeedService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;

    private final PostRepository postRepository;

    public FeedService(PostRepository postRepository) {
        this.postRepository = postRepository;
    }

    @Transactional(readOnly = true)
    public FeedResponse getGlobalFeed(Integer requestedLimit, String encodedCursor) {
        int limit = normalizeLimit(requestedLimit);
        Optional<FeedCursor> cursor = FeedCursor.decode(encodedCursor);

        List<Post> posts = cursor
                .map(feedCursor -> postRepository.findNextPage(
                        feedCursor.createdAt(),
                        feedCursor.id(),
                        PageRequest.of(0, limit + 1)
                ))
                .orElseGet(() -> postRepository.findFirstPage(PageRequest.of(0, limit + 1)));

        boolean hasNext = posts.size() > limit;
        List<Post> pageItems = hasNext ? posts.subList(0, limit) : posts;

        String nextCursor = null;
        if (hasNext && !pageItems.isEmpty()) {
            Post lastPost = pageItems.get(pageItems.size() - 1);
            nextCursor = new FeedCursor(lastPost.getCreatedAt(), lastPost.getId()).encode();
        }

        return new FeedResponse(
                pageItems.stream().map(PostResponse::from).toList(),
                nextCursor
        );
    }

    private int normalizeLimit(Integer requestedLimit) {
        if (requestedLimit == null || requestedLimit < 1) {
            return DEFAULT_LIMIT;
        }

        return Math.min(requestedLimit, MAX_LIMIT);
    }
}
