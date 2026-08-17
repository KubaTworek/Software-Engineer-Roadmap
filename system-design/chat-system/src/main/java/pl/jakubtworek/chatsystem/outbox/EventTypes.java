package pl.jakubtworek.chatsystem.outbox;

public final class EventTypes {
    private EventTypes() {}

    public static final String MESSAGE_CREATED = "message.created";
    public static final String MESSAGE_RECEIPT_UPDATED = "message.receipt.updated";
    public static final String PUSH_NOTIFICATION_REQUESTED = "push.notification.requested";
}
