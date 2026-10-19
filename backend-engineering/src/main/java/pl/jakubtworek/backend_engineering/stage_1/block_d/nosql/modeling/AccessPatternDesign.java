package pl.jakubtworek.backend_engineering.stage_1.block_d.nosql.modeling;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Łączy wymaganie aplikacji z fizycznym kształtem tabeli NoSQL.
 *
 * <p>To celowo nie jest generator schematu. Projektant jawnie zapisuje decyzję,
 * a {@link #evaluate(QueryShape)} pozwala sprawdzić, czy nowy query shape nadal
 * mieści się w kontrakcie tabeli, czy wymaga osobnej projekcji.</p>
 */
public record AccessPatternDesign(AccessPattern accessPattern, TableSchema table) {

    public AccessPatternDesign {
        accessPattern = Objects.requireNonNull(accessPattern, "accessPattern must not be null");
        table = Objects.requireNonNull(table, "table must not be null");
    }

    public DesignEvaluation evaluate(QueryShape query) {
        Objects.requireNonNull(query, "query must not be null");
        List<String> violations = new ArrayList<>();

        Set<String> missingPartitionKeys = Set.copyOf(table.partitionKey()).stream()
                .filter(field -> !query.equalityFilters().contains(field))
                .collect(java.util.stream.Collectors.toSet());
        if (!missingPartitionKeys.isEmpty()) {
            violations.add("missing equality filters for partition key: " + missingPartitionKeys);
        }

        if (!isPrefix(query.orderBy(), table.clusteringOrder())) {
            violations.add("requested order is not a prefix of clustering order");
        }

        Set<String> missingProjection = query.projectedFields().stream()
                .filter(field -> !table.projectedFields().contains(field))
                .collect(java.util.stream.Collectors.toSet());
        if (!missingProjection.isEmpty()) {
            violations.add("fields require another lookup: " + missingProjection);
        }

        return new DesignEvaluation(violations.isEmpty(), violations);
    }

    private static boolean isPrefix(List<String> requested, List<String> available) {
        return requested.size() <= available.size()
                && available.subList(0, requested.size()).equals(requested);
    }

    public record TableSchema(
            String tableName,
            List<String> partitionKey,
            List<String> clusteringOrder,
            Set<String> projectedFields,
            int expectedMaxItemsPerPartition
    ) {
        public TableSchema {
            if (tableName == null || tableName.isBlank()) {
                throw new IllegalArgumentException("tableName must not be blank");
            }
            partitionKey = List.copyOf(Objects.requireNonNull(partitionKey));
            clusteringOrder = List.copyOf(Objects.requireNonNull(clusteringOrder));
            projectedFields = Set.copyOf(Objects.requireNonNull(projectedFields));
            if (partitionKey.isEmpty()) {
                throw new IllegalArgumentException("partitionKey must not be empty");
            }
            if (expectedMaxItemsPerPartition <= 0) {
                throw new IllegalArgumentException("expectedMaxItemsPerPartition must be positive");
            }
        }
    }

    public record QueryShape(
            Set<String> equalityFilters,
            List<String> orderBy,
            Set<String> projectedFields
    ) {
        public QueryShape {
            equalityFilters = Set.copyOf(Objects.requireNonNull(equalityFilters));
            orderBy = List.copyOf(Objects.requireNonNull(orderBy));
            projectedFields = Set.copyOf(Objects.requireNonNull(projectedFields));
        }
    }

    public record DesignEvaluation(boolean supported, List<String> violations) {
        public DesignEvaluation {
            violations = List.copyOf(Objects.requireNonNull(violations));
            if (supported == !violations.isEmpty()) {
                throw new IllegalArgumentException("supported flag and violations disagree");
            }
        }
    }
}
