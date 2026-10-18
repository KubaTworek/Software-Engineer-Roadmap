package com.example.filestorage.sync;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ChangeLogRepository extends JpaRepository<ChangeLog, Long> {
    @Query(value = """
            SELECT log.* FROM change_log log
            JOIN change_log_audience audience ON audience.change_id = log.id
            WHERE audience.user_id = :userId AND log.id > :cursor
            ORDER BY log.id
            """, nativeQuery = true)
    List<ChangeLog> findVisibleChanges(@Param("userId") UUID userId,
                                       @Param("cursor") Long cursor,
                                       Pageable pageable);

    @Modifying
    @Query(value = """
            INSERT INTO change_log_audience(change_id, user_id)
            VALUES (:changeId, :userId)
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    void addAudience(@Param("changeId") Long changeId, @Param("userId") UUID userId);
}
