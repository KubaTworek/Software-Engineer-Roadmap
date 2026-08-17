package com.example.filestorage.search;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface SearchIndexRepository extends JpaRepository<SearchIndex, UUID> {
    @Query("select s from SearchIndex s where s.ownerId = :ownerId and lower(s.searchableText) like lower(concat('%', :query, '%'))")
    Page<SearchIndex> search(@Param("ownerId") UUID ownerId, @Param("query") String query, Pageable pageable);
    void deleteByResourceId(UUID resourceId);
}
