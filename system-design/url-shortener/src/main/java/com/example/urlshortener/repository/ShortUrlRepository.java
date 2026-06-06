package com.example.urlshortener.repository;

import com.example.urlshortener.model.ShortUrl;
import com.example.urlshortener.model.UrlStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ShortUrlRepository extends JpaRepository<ShortUrl, Long> {
    Optional<ShortUrl> findByShortCode(String shortCode);
    boolean existsByShortCode(String shortCode);
    long countByStatus(UrlStatus status);

    @Query(value = "SELECT nextval('url_id_seq')", nativeQuery = true)
    Long nextId();
}
