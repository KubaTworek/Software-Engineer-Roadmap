package pl.jakubtworek.backend_engineering.stage_1.block_d.nosql.graph;

import java.math.BigDecimal;
import java.util.Set;

/**
 * Przykład węzła produktu w graph DB.
 */
public class ProductNode {

    private final String id;
    private final String name;
    private final BigDecimal price;
    private final Set<String> categories;
    private final boolean active;

    public ProductNode(
            String id,
            String name,
            BigDecimal price,
            Set<String> categories,
            boolean active
    ) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.categories = Set.copyOf(categories);
        this.active = active;
    }

    public boolean belongsToCategory(String category) {
        return categories.contains(category);
    }

    public String id() { return id; }
    public String name() { return name; }
    public BigDecimal price() { return price; }
    public Set<String> categories() { return categories; }
    public boolean active() { return active; }
}
