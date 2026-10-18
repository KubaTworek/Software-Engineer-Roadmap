package com.example.newsfeed.user;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_blocks")
public class UserBlock {
    @EmbeddedId
    private UserBlockId id;

    protected UserBlock() { }

    public UserBlock(UserBlockId id) { this.id = id; }
}
