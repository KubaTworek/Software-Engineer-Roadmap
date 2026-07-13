package com.ridesharing.mvp.user;

import java.math.BigDecimal;
import java.util.UUID;

public record UserDto(UUID id, String email, String phoneNumber, String fullName, UserRole role, BigDecimal rating) {
    public static UserDto from(AppUser user) {
        return new UserDto(user.getId(), user.getEmail(), user.getPhoneNumber(), user.getFullName(), user.getRole(), user.getRating());
    }
}
