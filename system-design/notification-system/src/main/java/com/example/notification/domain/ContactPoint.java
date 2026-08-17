package com.example.notification.domain;

public record ContactPoint(String email, String phoneNumber, String pushToken) {
    public String valueFor(Channel channel) {
        return switch (channel) {
            case EMAIL -> email;
            case SMS -> phoneNumber;
            case PUSH -> pushToken;
            case IN_APP -> "in-app:user";
        };
    }

    public boolean hasContactFor(Channel channel) {
        return switch (channel) {
            case EMAIL -> email != null && !email.isBlank();
            case SMS -> phoneNumber != null && !phoneNumber.isBlank();
            case PUSH -> pushToken != null && !pushToken.isBlank();
            case IN_APP -> true;
        };
    }
}
