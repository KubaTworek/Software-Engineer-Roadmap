package com.example.newsfeed.events;

public final class NewsFeedTopics {
    public static final String POST_CREATED = "newsfeed.post.created";
    public static final String POST_DELETED = "newsfeed.post.deleted";
    public static final String POST_LIKED = "newsfeed.post.liked";
    public static final String POST_UNLIKED = "newsfeed.post.unliked";
    public static final String COMMENT_CREATED = "newsfeed.comment.created";
    public static final String COMMENT_DELETED = "newsfeed.comment.deleted";
    public static final String FOLLOW_CREATED = "newsfeed.follow.created";
    public static final String FOLLOW_DELETED = "newsfeed.follow.deleted";
    public static final String DLQ = "newsfeed.dlq";

    private NewsFeedTopics() {
    }
}
