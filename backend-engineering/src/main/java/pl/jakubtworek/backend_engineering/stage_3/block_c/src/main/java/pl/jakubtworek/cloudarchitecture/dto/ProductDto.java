package pl.jakubtworek.backend_engineering.stage_3.block_c.src.main.java.pl.jakubtworek.cloudarchitecture.dto;

import java.math.BigDecimal;

/**
 * Data Transfer Object used at the API boundary.
 *
 * DTOs isolate public API contracts from internal persistence models.
 */
public record ProductDto(Long id, String name, BigDecimal price) {}
