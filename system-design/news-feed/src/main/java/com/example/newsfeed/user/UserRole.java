package com.example.newsfeed.user;

/** Role są celowo małe: autoryzacja moderacji nie może opierać się na samym zalogowaniu. */
public enum UserRole {
    USER,
    MODERATOR,
    ADMIN
}
