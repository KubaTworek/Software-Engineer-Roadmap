package pl.jakubtworek.backend_engineering.stage_2.block_a.api_design;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Kanoniczny model zasobu używany przez adapter HTTP. Synchronizacja symuluje
 * atomową granicę repozytorium; produkcyjnie wersję chroni warunkowy UPDATE.
 */
public final class OrderService {

    private final Clock clock;
    private final Supplier<UUID> idSupplier;
    private final Map<UUID, OrderResource> orders = new HashMap<>();
    private final Map<String, IdempotencyRecord> idempotencyRecords = new HashMap<>();

    public OrderService(Clock clock, Supplier<UUID> idSupplier) {
        this.clock = Objects.requireNonNull(clock);
        this.idSupplier = Objects.requireNonNull(idSupplier);
    }

    public OrderService(Clock clock) {
        this(clock, UUID::randomUUID);
    }

    synchronized CommandResult create(String idempotencyKey, OrderCommands.Create command) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw ApiFailure.badRequest("Idempotency-Key header must not be blank");
        }
        validateItems(command.items());

        IdempotencyRecord previous = idempotencyRecords.get(idempotencyKey);
        if (previous != null) {
            if (!previous.fingerprint().equals(command.fingerprint())) {
                throw ApiFailure.conflict("The key was already used with a different request");
            }
            return new CommandResult(previous.response(), true);
        }

        UUID id = idSupplier.get();
        OrderResource created = new OrderResource(
                id,
                command.customerEmail(),
                command.items(),
                command.expedited(),
                OrderResource.Status.NEW,
                1,
                clock.instant()
        );
        orders.put(id, created);
        idempotencyRecords.put(idempotencyKey, new IdempotencyRecord(command.fingerprint(), created));
        return new CommandResult(created, false);
    }

    public synchronized OrderResource get(UUID id) {
        return requireOrder(id);
    }

    synchronized OrderResource replace(UUID id, String ifMatch, OrderCommands.Replace command) {
        validateItems(command.items());
        OrderResource current = requireOrder(id);
        EntityTags.requireCurrent(ifMatch, current.version());

        OrderResource replaced = new OrderResource(
                current.id(),
                command.customerEmail(),
                command.items(),
                command.expedited(),
                current.status(),
                current.version() + 1,
                current.createdAt()
        );
        orders.put(id, replaced);
        return replaced;
    }

    synchronized OrderResource patch(UUID id, String ifMatch, OrderCommands.Patch command) {
        if (command.isEmpty()) {
            throw ApiFailure.badRequest("PATCH must contain at least one mutable field");
        }
        OrderResource current = requireOrder(id);
        EntityTags.requireCurrent(ifMatch, current.version());

        OrderResource patched = new OrderResource(
                current.id(),
                command.customerEmail() == null ? current.customerEmail() : command.customerEmail(),
                current.items(),
                command.expedited() == null ? current.expedited() : command.expedited(),
                current.status(),
                current.version() + 1,
                current.createdAt()
        );
        orders.put(id, patched);
        return patched;
    }

    public synchronized void delete(UUID id, String ifMatch) {
        OrderResource current = requireOrder(id);
        EntityTags.requireCurrent(ifMatch, current.version());
        orders.remove(id);
    }

    public synchronized OrderResource cancel(UUID id) {
        OrderResource current = requireOrder(id);
        if (current.status() == OrderResource.Status.CANCELLED) {
            return current;
        }
        OrderResource cancelled = new OrderResource(
                current.id(),
                current.customerEmail(),
                current.items(),
                current.expedited(),
                OrderResource.Status.CANCELLED,
                current.version() + 1,
                current.createdAt()
        );
        orders.put(id, cancelled);
        return cancelled;
    }

    public synchronized OrderPage list(int limit, String cursor, OrderResource.Status status, Sort sort) {
        if (limit < 1 || limit > 100) {
            throw ApiFailure.badRequest("limit must be between 1 and 100");
        }
        Cursor decoded = cursor == null || cursor.isBlank() ? null : Cursor.decode(cursor);
        Comparator<OrderResource> comparator = Comparator
                .comparing(OrderResource::createdAt)
                .thenComparing(OrderResource::id);
        if (sort == Sort.CREATED_DESC) {
            comparator = comparator.reversed();
        }

        List<OrderResource> matching = orders.values().stream()
                .filter(order -> status == null || order.status() == status)
                .sorted(comparator)
                .filter(order -> decoded == null || isAfter(order, decoded, sort))
                .toList();

        List<OrderResource> page = new ArrayList<>(matching.subList(0, Math.min(limit, matching.size())));
        String next = matching.size() > limit ? Cursor.from(page.get(page.size() - 1)).encode() : null;
        return new OrderPage(page, next);
    }

    private static boolean isAfter(OrderResource order, Cursor cursor, Sort sort) {
        int timestamp = order.createdAt().compareTo(cursor.createdAt());
        int comparison = timestamp != 0 ? timestamp : order.id().compareTo(cursor.id());
        return sort == Sort.CREATED_ASC ? comparison > 0 : comparison < 0;
    }

    private OrderResource requireOrder(UUID id) {
        OrderResource order = orders.get(id);
        if (order == null) {
            throw ApiFailure.notFound("Order " + id);
        }
        return order;
    }

    private static void validateItems(List<OrderResource.LineItem> items) {
        Set<String> skus = new HashSet<>();
        for (OrderResource.LineItem item : items) {
            if (!skus.add(item.sku())) {
                throw ApiFailure.domainRule("An order cannot contain duplicate SKU " + item.sku());
            }
        }
    }

    public enum Sort {
        CREATED_ASC,
        CREATED_DESC;

        static Sort parse(String value) {
            return switch (value) {
                case "createdAt" -> CREATED_ASC;
                case "-createdAt" -> CREATED_DESC;
                default -> throw ApiFailure.badRequest("sort must be createdAt or -createdAt");
            };
        }
    }

    record CommandResult(OrderResource order, boolean replayed) {
    }

    private record IdempotencyRecord(String fingerprint, OrderResource response) {
    }

    private record Cursor(Instant createdAt, UUID id) {
        static Cursor from(OrderResource resource) {
            return new Cursor(resource.createdAt(), resource.id());
        }

        String encode() {
            // Instant.toString() zachowuje nanosekundy; obcięcie do milisekund
            // mogłoby ponownie zwrócić ostatni rekord poprzedniej strony.
            String raw = createdAt + "|" + id;
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        }

        static Cursor decode(String value) {
            try {
                String raw = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
                String[] parts = raw.split("\\|", 2);
                return new Cursor(Instant.parse(parts[0]), UUID.fromString(parts[1]));
            } catch (RuntimeException exception) {
                throw ApiFailure.badRequest("cursor is malformed");
            }
        }
    }
}
