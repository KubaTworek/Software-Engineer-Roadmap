package pl.jakubtworek.booking.service.async;

public record SideEffectResult(String name, boolean success, String message) {
    public static SideEffectResult success(String name) {
        return new SideEffectResult(name, true, "OK");
    }

    public static SideEffectResult failure(String name, Throwable error) {
        String message = error == null ? "unknown" : error.getClass().getSimpleName() + ": " + error.getMessage();
        return new SideEffectResult(name, false, message);
    }
}
