package com.example.urlshortener.repository;

import com.example.urlshortener.model.UrlModerationAudit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UrlModerationAuditRepository extends JpaRepository<UrlModerationAudit, Long> {}
