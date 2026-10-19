package com.example.videostreaming.qoe;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.UUID;

public interface QoeEventRepository extends JpaRepository<QoeEvent, UUID> {
    @Modifying
    @Query("delete from QoeEvent event where event.ingestedAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
