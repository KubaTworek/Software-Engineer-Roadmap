package com.example.videostreaming.catalog;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface VideoRepository extends JpaRepository<Video, UUID> {
    Page<Video> findByStatusAndVisibilityOrderByPublishedAtDesc(VideoStatus status, VideoVisibility visibility, Pageable pageable);
}
