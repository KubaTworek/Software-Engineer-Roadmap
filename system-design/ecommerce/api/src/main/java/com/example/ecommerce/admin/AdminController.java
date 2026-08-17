package com.example.ecommerce.admin;

import com.example.ecommerce.audit.AdminAuditLogRepository;
import com.example.ecommerce.audit.AdminAuditService;
import com.example.ecommerce.audit.dto.AuditDtos;
import com.example.ecommerce.auth.AppUser;
import com.example.ecommerce.catalog.CatalogService;
import com.example.ecommerce.catalog.dto.CatalogDtos;
import com.example.ecommerce.inventory.InventoryService;
import com.example.ecommerce.order.OrderService;
import com.example.ecommerce.order.dto.OrderDtos;
import com.example.ecommerce.promotion.PromotionAdminService;
import com.example.ecommerce.promotion.dto.PromotionDtos;
import com.example.ecommerce.pricing.DynamicPricingService;
import com.example.ecommerce.pricing.dto.PricingDtos;
import com.example.ecommerce.marketplace.MarketplaceService;
import com.example.ecommerce.marketplace.dto.MarketplaceDtos;
import com.example.ecommerce.warehouse.WarehouseService;
import com.example.ecommerce.warehouse.dto.WarehouseDtos;
import com.example.ecommerce.returns.ReturnService;
import com.example.ecommerce.returns.dto.ReturnDtos;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid; import jakarta.validation.constraints.Min; import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize; import org.springframework.security.core.annotation.AuthenticationPrincipal; import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {
    private final CatalogService catalog; private final InventoryService inventory; private final OrderService orders; private final AdminAuditService audit; private final AdminAuditLogRepository auditLogs;
    private final PromotionAdminService promotions;
    private final DynamicPricingService pricing;
    private final MarketplaceService marketplace;
    private final WarehouseService warehouses;
    private final ReturnService returns;
    public AdminController(CatalogService catalog, InventoryService inventory, OrderService orders, AdminAuditService audit, AdminAuditLogRepository auditLogs) { this.catalog=catalog; this.inventory=inventory; this.orders=orders; this.audit=audit; this.auditLogs=auditLogs; }
    @PostMapping("/products")
    public CatalogDtos.ProductResponse createProduct(@AuthenticationPrincipal AppUser admin, @Valid @RequestBody CatalogDtos.CreateProductRequest request, HttpServletRequest httpRequest) {
        CatalogDtos.ProductResponse response = catalog.createProduct(request); audit.log(admin, "CREATE_PRODUCT", "Product", response.id().toString(), null, response, httpRequest); return response;
    }
    @PatchMapping("/inventory/{variantId}")
    public void updateStock(@AuthenticationPrincipal AppUser admin, @PathVariable Long variantId, @Valid @RequestBody UpdateStockRequest request, HttpServletRequest httpRequest) {
        inventory.updateStock(variantId, request.availableQuantity()); audit.log(admin, "UPDATE_INVENTORY", "ProductVariant", variantId.toString(), null, request, httpRequest);
    }
    @GetMapping("/orders") public List<OrderDtos.OrderResponse> orders() { return orders.allOrders(); }
    @GetMapping("/audit-logs")
    public List<AuditDtos.AdminAuditLogResponse> auditLogs() {
        return auditLogs.findTop100ByOrderByCreatedAtDesc().stream().map(log -> new AuditDtos.AdminAuditLogResponse(log.getId(), log.getAdminUserId(), log.getAdminEmail(), log.getAction(), log.getEntityType(), log.getEntityId(), log.getCreatedAt())).toList();
    }
    
    @PostMapping("/promotions")
    public Long createPromotion(@Valid @RequestBody PromotionDtos.CreatePromotionRequest request) {
        return promotions.createPromotion(request);
    }

    @PostMapping("/coupons")
    public Long createCoupon(@Valid @RequestBody PromotionDtos.CreateCouponRequest request) {
        return promotions.createCoupon(request);
    }

    @PostMapping("/pricing/rules")
    public Long createDynamicPriceRule(@Valid @RequestBody PricingDtos.CreateDynamicPriceRuleRequest request) {
        return pricing.createRule(request);
    }

    @PostMapping("/marketplace/sellers/{sellerId}/activate")
    public MarketplaceDtos.SellerResponse activateSeller(@PathVariable Long sellerId) {
        return marketplace.activateSeller(sellerId);
    }

    @PostMapping("/warehouses")
    public WarehouseDtos.WarehouseResponse createWarehouse(@Valid @RequestBody WarehouseDtos.CreateWarehouseRequest request) {
        return warehouses.create(request);
    }

    @PatchMapping("/warehouses/stock")
    public WarehouseDtos.WarehouseStockResponse setWarehouseStock(@Valid @RequestBody WarehouseDtos.SetWarehouseStockRequest request) {
        return warehouses.setStock(request);
    }

    @PostMapping("/returns/{returnId}/approve")
    public ReturnDtos.ReturnResponse approveReturn(@PathVariable Long returnId) {
        return returns.approve(returnId);
    }

    @PostMapping("/returns/{returnId}/refund")
    public ReturnDtos.ReturnResponse refundReturn(@PathVariable Long returnId) {
        return returns.markRefunded(returnId);
    }

    public record UpdateStockRequest(@NotNull @Min(0) Integer availableQuantity) {}

}
