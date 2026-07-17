package com.example.searchindexer.outbox;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedSearchEventRepository extends JpaRepository<ProcessedSearchEvent, Long> {
}
