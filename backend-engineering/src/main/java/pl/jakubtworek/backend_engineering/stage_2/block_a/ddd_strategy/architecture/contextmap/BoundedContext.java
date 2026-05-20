package pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_strategy.architecture.contextmap;

import java.util.List;

// Represents a strategic DDD bounded context.
// A bounded context owns its model, language, contracts, and persistence boundary.
public final class BoundedContext {

    private final String name;
    private final ContextType type;
    private final String team;
    private final String database;
    private final List<String> services;

    public BoundedContext(
            String name,
            ContextType type,
            String team,
            String database,
            List<String> services
    ) {
        this.name = name;
        this.type = type;
        this.team = team;
        this.database = database;
        this.services = List.copyOf(services);
    }

    public String name() {
        return name;
    }

    public ContextType type() {
        return type;
    }

    public String team() {
        return team;
    }

    public String database() {
        return database;
    }

    public List<String> services() {
        return services;
    }
}