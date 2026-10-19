package com.ridesharing.mvp.payment;

import java.util.UUID;

/** Trigger sagi płatniczej emitowany w transakcji utworzenia przejazdu. */
public record PaymentAuthorizationRequestedEvent(UUID rideId) {
}
