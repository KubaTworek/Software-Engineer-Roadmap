package pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_tactic.domain.model;

// Marker interface for aggregate roots.
// Only aggregate roots should be loaded and saved through repositories.
public interface AggregateRoot<ID> {

    ID id();
}