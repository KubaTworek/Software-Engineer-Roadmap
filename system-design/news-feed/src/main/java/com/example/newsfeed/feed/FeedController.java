package com.example.newsfeed.feed;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/feed")
public class FeedController {

    private final FeedService feedService;

    public FeedController(FeedService feedService) {
        this.feedService = feedService;
    }

    @GetMapping
    public FeedResponse getGlobalFeed(
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor
    ) {
        return feedService.getGlobalFeed(limit, cursor);
    }
}
