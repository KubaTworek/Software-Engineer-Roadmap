package com.example.newsfeed.comment;

import java.util.List;
public record CommentPageResponse(List<CommentResponse> items, String nextCursor) {}
