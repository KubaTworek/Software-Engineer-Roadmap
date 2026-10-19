package com.example.newsfeed.outbox;

public enum DomainEventStatus {
    PENDING,
    CLAIMED,
    PROCESSED,
    FAILED
}
