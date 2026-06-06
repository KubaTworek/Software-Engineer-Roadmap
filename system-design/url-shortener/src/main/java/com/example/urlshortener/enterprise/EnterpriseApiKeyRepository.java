package com.example.urlshortener.enterprise;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EnterpriseApiKeyRepository extends JpaRepository<EnterpriseApiKey, Long> {
    Optional<EnterpriseApiKey> findByKeyHash(String keyHash);
}
