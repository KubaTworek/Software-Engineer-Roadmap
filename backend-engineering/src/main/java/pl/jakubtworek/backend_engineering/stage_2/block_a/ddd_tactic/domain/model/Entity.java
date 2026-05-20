package pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_tactic.domain.model;

// Marker interface for entities.
// Entities are compared by identity, not by all field values.
public interface Entity<ID> {

    ID id();
}