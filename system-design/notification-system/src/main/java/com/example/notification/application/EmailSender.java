package com.example.notification.application;

import com.example.notification.domain.Notification;

public interface EmailSender {

    void send(Notification notification);
}
