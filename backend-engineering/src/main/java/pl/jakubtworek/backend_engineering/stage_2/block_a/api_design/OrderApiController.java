package pl.jakubtworek.backend_engineering.stage_2.block_a.api_design;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@Profile("api-design-lab")
@RequestMapping("/api/v1")
public final class OrderApiController {

    private static final String MERGE_PATCH = "application/merge-patch+json";

    private final OrderService orders;
    private final AsyncCancellationService cancellations;

    public OrderApiController(OrderService orders, AsyncCancellationService cancellations) {
        this.orders = orders;
        this.cancellations = cancellations;
    }

    @PostMapping(path = "/orders", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<OrderResource> create(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request
    ) {
        OrderService.CommandResult result = orders.create(idempotencyKey, request.toCommand());
        URI location = URI.create("/api/v1/orders/" + result.order().id());
        return ResponseEntity.created(location)
                .eTag(EntityTags.fromVersion(result.order().version()))
                .header("Idempotency-Replayed", Boolean.toString(result.replayed()))
                .body(result.order());
    }

    @GetMapping("/orders/{id}")
    ResponseEntity<OrderResource> get(
            @PathVariable UUID id,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch
    ) {
        OrderResource order = orders.get(id);
        String etag = EntityTags.fromVersion(order.version());
        if (etag.equals(ifNoneMatch)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).build();
        }
        return ResponseEntity.ok().eTag(etag).body(order);
    }

    @GetMapping("/orders")
    OrderPage list(
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) OrderResource.Status status,
            @RequestParam(defaultValue = "createdAt") String sort
    ) {
        return orders.list(limit, cursor, status, OrderService.Sort.parse(sort));
    }

    @PutMapping(path = "/orders/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<OrderResource> replace(
            @PathVariable UUID id,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody ReplaceOrderRequest request
    ) {
        OrderResource order = orders.replace(id, ifMatch, request.toCommand());
        return ResponseEntity.ok().eTag(EntityTags.fromVersion(order.version())).body(order);
    }

    @PatchMapping(path = "/orders/{id}", consumes = MERGE_PATCH)
    ResponseEntity<OrderResource> patch(
            @PathVariable UUID id,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody PatchOrderRequest request
    ) {
        OrderResource order = orders.patch(id, ifMatch, request.toCommand());
        return ResponseEntity.ok().eTag(EntityTags.fromVersion(order.version())).body(order);
    }

    @DeleteMapping("/orders/{id}")
    ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch
    ) {
        orders.delete(id, ifMatch);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/orders/{id}/cancellations")
    ResponseEntity<AsyncCancellationService.Operation> cancel(@PathVariable UUID id) {
        AsyncCancellationService.Operation operation = cancellations.start(id);
        return ResponseEntity.accepted()
                .location(URI.create("/api/v1/operations/" + operation.id()))
                .body(operation);
    }

    @GetMapping("/operations/{id}")
    AsyncCancellationService.Operation operation(@PathVariable UUID id) {
        return cancellations.get(id);
    }

    public record LineItemRequest(
            @NotBlank @Size(max = 64) String sku,
            @Min(1) @Max(10_000) int quantity
    ) {
        @JsonAnySetter
        public void rejectUnknownProperty(String property, Object ignoredValue) {
            throw ApiFailure.badRequest("Unknown property: " + property);
        }

        OrderResource.LineItem toDomain() {
            return new OrderResource.LineItem(sku, quantity);
        }
    }

    public record CreateOrderRequest(
            @NotBlank @Email @Size(max = 254) String customerEmail,
            @NotEmpty @Size(max = 100) List<@NotNull @Valid LineItemRequest> items,
            boolean expedited
    ) {
        @JsonAnySetter
        public void rejectUnknownProperty(String property, Object ignoredValue) {
            throw ApiFailure.badRequest("Unknown property: " + property);
        }

        OrderCommands.Create toCommand() {
            return new OrderCommands.Create(customerEmail, items.stream().map(LineItemRequest::toDomain).toList(), expedited);
        }
    }

    public record ReplaceOrderRequest(
            @NotBlank @Email @Size(max = 254) String customerEmail,
            @NotEmpty @Size(max = 100) List<@NotNull @Valid LineItemRequest> items,
            boolean expedited
    ) {
        @JsonAnySetter
        public void rejectUnknownProperty(String property, Object ignoredValue) {
            throw ApiFailure.badRequest("Unknown property: " + property);
        }

        OrderCommands.Replace toCommand() {
            return new OrderCommands.Replace(customerEmail, items.stream().map(LineItemRequest::toDomain).toList(), expedited);
        }
    }

    public record PatchOrderRequest(
            @Email @Size(max = 254) String customerEmail,
            Boolean expedited
    ) {
        @JsonAnySetter
        public void rejectUnknownProperty(String property, Object ignoredValue) {
            throw ApiFailure.badRequest("Unknown property: " + property);
        }

        OrderCommands.Patch toCommand() {
            return new OrderCommands.Patch(customerEmail, expedited);
        }
    }
}
