package pl.jakubtworek.backend_engineering.stage_2.block_b.cdc_reconciliation;

public record AuthoritativeOrder(String id, String status, long totalCents, long version) {

    public AuthoritativeOrder {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id is required");
        }
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("status is required");
        }
        if (totalCents < 0 || version <= 0) {
            throw new IllegalArgumentException("totalCents must be non-negative and version positive");
        }
    }
}
