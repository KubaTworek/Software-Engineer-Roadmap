package com.example.videostreaming.upload;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface UploadRepository extends JpaRepository<Upload, UUID> {}
