package com.example.newsfeed.user;

import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
public record UserBlockId(UUID blockerId, UUID blockedId) implements Serializable { }
