package pl.jakubtworek.backend_engineering.stage_1.block_c.mvc;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Spring MVC configuration.
 *
 * WebMvcConfigurer allows customizing MVC pipeline without replacing
 * Spring Boot auto-configuration.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthUserArgumentResolver authUserArgumentResolver;
    private final RequestLoggingInterceptor requestLoggingInterceptor;

    public WebMvcConfig(
            AuthUserArgumentResolver authUserArgumentResolver,
            RequestLoggingInterceptor requestLoggingInterceptor
    ) {
        this.authUserArgumentResolver = authUserArgumentResolver;
        this.requestLoggingInterceptor = requestLoggingInterceptor;
    }

    /**
     * Registers custom argument resolver.
     *
     * After registration, controller methods can accept AuthUser parameter.
     */
    @Override
    public void addArgumentResolvers(
            List<HandlerMethodArgumentResolver> resolvers
    ) {
        resolvers.add(authUserArgumentResolver);
    }

    /**
     * Registers HandlerInterceptor.
     *
     * Interceptors run around controller execution:
     * - preHandle before controller,
     * - postHandle after controller,
     * - afterCompletion after request completion.
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(requestLoggingInterceptor)
                .addPathPatterns("/api/**");
    }
}