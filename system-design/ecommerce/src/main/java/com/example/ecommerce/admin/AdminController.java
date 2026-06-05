package com.example.ecommerce.admin;

import com.example.ecommerce.catalog.CatalogService;
import com.example.ecommerce.catalog.dto.CatalogDtos;
import com.example.ecommerce.inventory.InventoryService;
import com.example.ecommerce.order.OrderService;
import com.example.ecommerce.order.dto.OrderDtos;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {
    private final CatalogService catalog;
    private final InventoryService inventory;
    private final OrderService orders;

    public AdminController(CatalogService catalog, InventoryService inventory, OrderService orders) {
        this.catalog = catalog;
        this.inventory = inventory;
        this.orders = orders;
    }

    @PostMapping("/products")
    public CatalogDtos.ProductResponse createProduct(@Valid @RequestBody CatalogDtos.CreateProductRequest request) {
        return catalog.createProduct(request);
    }

    @PatchMapping("/inventory/{variantId}")
    public void updateStock(@PathVariable Long variantId, @Valid @RequestBody UpdateStockRequest request) {
        inventory.updateStock(variantId, request.availableQuantity());
    }

    @GetMapping("/orders")
    public List<OrderDtos.OrderResponse> orders() {
        return orders.allOrders();
    }

    public record UpdateStockRequest(@NotNull @Min(0) Integer availableQuantity) {}
}
