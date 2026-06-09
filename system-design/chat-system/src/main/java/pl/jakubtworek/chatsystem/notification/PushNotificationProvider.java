package pl.jakubtworek.chatsystem.notification;

public interface PushNotificationProvider {
    void send(PushPayload payload);
}
