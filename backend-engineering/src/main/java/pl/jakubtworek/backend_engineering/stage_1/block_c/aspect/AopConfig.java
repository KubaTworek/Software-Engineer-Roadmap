package pl.jakubtworek.backend_engineering.stage_1.block_c.aspect;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * Enables Spring AOP proxy support.
 *
 * Spring creates proxy objects for beans
 * matched by aspects.
 *
 * exposeProxy=true allows using:
 * AopContext.currentProxy()
 *
 * Useful for demonstrating self-invocation workaround.
 */
@Configuration
@EnableAspectJAutoProxy(exposeProxy = true)
public class AopConfig {
}