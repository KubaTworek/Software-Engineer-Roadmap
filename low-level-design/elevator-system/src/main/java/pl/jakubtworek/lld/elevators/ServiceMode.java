package pl.jakubtworek.lld.elevators;

/**
 * DRAINING kończy przyjęte cele bez przyjmowania nowych; IN_SERVICE może oznaczać windę bezczynną.
 */
public enum ServiceMode {

    IN_SERVICE, DRAINING, OUT_OF_SERVICE
}
