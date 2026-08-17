package com.example.videostreaming.transcoding;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface TranscodingJobRepository extends JpaRepository<TranscodingJob, UUID> {
    List<TranscodingJob> findByStatusOrderByCreatedAtAsc(TranscodingJobStatus status, Pageable pageable);
}
