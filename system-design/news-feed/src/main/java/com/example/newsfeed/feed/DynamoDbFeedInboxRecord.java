package com.example.newsfeed.feed;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Rekord feed inbox zapisany w DynamoDB.
 *
 * Jeden rekord oznacza:
 * "konkretny post powinien pojawić się w feedzie konkretnego użytkownika".
 *
 * Model tabeli:
 * - partition key: userId
 * - sort key: createdAtPostId
 *
 * Dzięki temu wszystkie elementy feedu jednego użytkownika są w jednej partycji
 * i można je czytać w kolejności od najnowszych do najstarszych.
 */
@DynamoDbBean
public class DynamoDbFeedInboxRecord {

    private String userId;
    private String createdAtPostId;
    private String postId;
    private String authorId;
    private double score;
    private String source;
    private int shardId;
    private Instant createdAt;
    private Long expiresAtEpochSeconds;

    /**
     * Partition key.
     *
     * Wszystkie wpisy feedu konkretnego użytkownika mają ten sam userId.
     */
    @DynamoDbPartitionKey
    public String getUserId() {
        return userId;
    }

    /**
     * Sort key.
     *
     * Format:
     * createdAt#postId
     *
     * Umożliwia stabilną paginację po czasie i po ID posta.
     */
    @DynamoDbSortKey
    public String getCreatedAtPostId() {
        return createdAtPostId;
    }

    public String getPostId() {
        return postId;
    }

    public String getAuthorId() {
        return authorId;
    }

    public double getScore() {
        return score;
    }

    public String getSource() {
        return source;
    }

    public int getShardId() {
        return shardId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Pole pod TTL w DynamoDB.
     *
     * DynamoDB TTL wymaga wartości epoch seconds.
     * Można go włączyć po stronie AWS dla tej kolumny.
     */
    public Long getExpiresAtEpochSeconds() {
        return expiresAtEpochSeconds;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public void setCreatedAtPostId(String createdAtPostId) {
        this.createdAtPostId = createdAtPostId;
    }

    public void setPostId(String postId) {
        this.postId = postId;
    }

    public void setAuthorId(String authorId) {
        this.authorId = authorId;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public void setShardId(int shardId) {
        this.shardId = shardId;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public void setExpiresAtEpochSeconds(Long expiresAtEpochSeconds) {
        this.expiresAtEpochSeconds = expiresAtEpochSeconds;
    }
}