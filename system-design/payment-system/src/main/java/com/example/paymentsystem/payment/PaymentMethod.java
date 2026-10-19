package com.example.paymentsystem.payment;

/**
 * Capability model keeps protocol differences explicit. An adapter may hide
 * JSON and HTTP details, but it must not pretend that every method supports
 * authorization, capture or disputes in the same way.
 */
public enum PaymentMethod {
    CARD(true, true, true, false, false),
    BLIK(false, true, false, true, true),
    BANK_REDIRECT(false, true, false, true, true),
    DIGITAL_WALLET(true, true, true, false, false),
    INTERNAL_BALANCE(false, true, false, false, false);

    private final boolean manualCapture;
    private final boolean refunds;
    private final boolean chargebacks;
    private final boolean redirect;
    private final boolean asynchronousConfirmation;

    PaymentMethod(boolean manualCapture, boolean refunds, boolean chargebacks,
                  boolean redirect, boolean asynchronousConfirmation) {
        this.manualCapture = manualCapture;
        this.refunds = refunds;
        this.chargebacks = chargebacks;
        this.redirect = redirect;
        this.asynchronousConfirmation = asynchronousConfirmation;
    }

    public boolean supportsManualCapture() { return manualCapture; }
    public boolean supportsRefunds() { return refunds; }
    public boolean supportsChargebacks() { return chargebacks; }
    public boolean requiresRedirect() { return redirect; }
    public boolean confirmsAsynchronously() { return asynchronousConfirmation; }
}
