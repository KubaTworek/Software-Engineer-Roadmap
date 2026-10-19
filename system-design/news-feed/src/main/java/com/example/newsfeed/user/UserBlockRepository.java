package com.example.newsfeed.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

public interface UserBlockRepository extends JpaRepository<UserBlock, UserBlockId> {
    @Query("""
            SELECT CASE WHEN count(b) > 0 THEN true ELSE false END
            FROM UserBlock b
            WHERE (b.id.blockerId = :left AND b.id.blockedId = :right)
               OR (b.id.blockerId = :right AND b.id.blockedId = :left)
            """)
    boolean existsInEitherDirection(UUID left, UUID right);
}
