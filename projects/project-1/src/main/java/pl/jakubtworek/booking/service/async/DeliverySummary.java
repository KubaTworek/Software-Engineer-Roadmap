package pl.jakubtworek.booking.service.async;

public record DeliverySummary(SideEffectResult email, SideEffectResult notification) {
    public boolean allDelivered() {
        return email.success() && notification.success();
    }
}
