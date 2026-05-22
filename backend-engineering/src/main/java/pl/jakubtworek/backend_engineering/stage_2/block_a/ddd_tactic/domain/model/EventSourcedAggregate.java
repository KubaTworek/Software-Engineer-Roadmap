package pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_tactic.domain.model;

import java.util.ArrayList;
import java.util.List;

// Base class for aggregates that collect domain events.
// Application services can publish these events after saving the aggregate.
public abstract class EventSourcedAggregate<ID> implements AggregateRoot<ID> {

    private final List<DomainEvent> domainEvents = new ArrayList<>();

    protected void record(DomainEvent event) {
        domainEvents.add(event);
    }

    public List<DomainEvent> pullDomainEvents() {
        List<DomainEvent> events = List.copyOf(domainEvents);
        domainEvents.clear();
        return events;
    }
}