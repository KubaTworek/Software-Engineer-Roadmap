package com.example.newsfeed.ranking;
import com.example.newsfeed.post.Post;
public record RankedCandidate(Post post, double score, String reason, String source) {}
