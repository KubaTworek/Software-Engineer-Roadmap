package com.example.newsfeed.celebrity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CelebrityAuthorRepository extends JpaRepository<CelebrityAuthor, UUID> {
}
