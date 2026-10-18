package com.ridesharing.mvp.payment;

import java.util.UUID;

/** Capture jest efektem zakończenia kursu, ale wykonuje się dopiero po commicie. */
public record PaymentCaptureRequestedEvent(UUID rideId) {
}
