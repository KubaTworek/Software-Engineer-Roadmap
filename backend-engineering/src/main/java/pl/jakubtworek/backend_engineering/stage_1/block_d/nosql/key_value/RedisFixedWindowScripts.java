package pl.jakubtworek.backend_engineering.stage_1.block_d.nosql.key_value;

/**
 * Server-side Redis operations used by the fixed-window counter example.
 *
 * <p>A separate {@code INCR} followed by {@code PEXPIRE} is not atomic. A process
 * failure between those commands can leave a counter without a TTL. Executing
 * both decisions in one Lua script makes Redis apply them as one operation.</p>
 */
public final class RedisFixedWindowScripts {

    public static final String INCREMENT_AND_SET_TTL = """
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
                redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return current
            """;

    private RedisFixedWindowScripts() {
    }
}
