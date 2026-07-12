package com.example.videostreaming.live;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LiveStreamRepository extends JpaRepository<LiveStream, UUID> {
    Optional<LiveStream> findByStreamKey(String streamKey);
    Page<LiveStream> findByStatusOrderByCreatedAtDesc(LiveStatus status, Pageable pageable);
    List<LiveStream> findTop20ByStatusInOrderByCreatedAtDesc(List<LiveStatus> statuses);
}
