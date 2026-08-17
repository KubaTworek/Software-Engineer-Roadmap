package com.ridesharing.mvp.scaling;

import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Component
public class ShardedOperation {
    private final CityShardResolver resolver;

    public ShardedOperation(CityShardResolver resolver) { this.resolver = resolver; }

    public <T> T runForCity(String cityId, Supplier<T> operation) {
        try {
            ShardContext.set(resolver.resolve(cityId));
            return operation.get();
        } finally {
            ShardContext.clear();
        }
    }
}
