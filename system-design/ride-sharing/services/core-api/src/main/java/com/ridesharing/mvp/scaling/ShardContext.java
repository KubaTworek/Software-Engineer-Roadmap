package com.ridesharing.mvp.scaling;

public final class ShardContext {
    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();
    private ShardContext() {}
    public static void set(String shard) { CURRENT.set(shard); }
    public static String get() { return CURRENT.get(); }
    public static void clear() { CURRENT.remove(); }
}
