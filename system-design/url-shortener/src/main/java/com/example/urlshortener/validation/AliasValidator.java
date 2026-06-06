package com.example.urlshortener.validation;

import com.example.urlshortener.exception.ReservedAliasException;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class AliasValidator {
    private static final Set<String> RESERVED_ALIASES = Set.of(
        "api",
        "admin",
        "login",
        "logout",
        "signup",
        "health",
        "metrics",
        "actuator",
        "robots.txt",
        "favicon.ico"
    );

    public void validateNotReserved(String alias) {
        if (alias != null && RESERVED_ALIASES.contains(alias)) {
            throw new ReservedAliasException(alias);
        }
    }
}
