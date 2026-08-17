package com.example.newsfeed.config;

import com.example.newsfeed.auth.AuthService;
import com.example.newsfeed.auth.CurrentUserArgumentResolver;
import com.example.newsfeed.ratelimit.RateLimitInterceptor;
import com.example.newsfeed.ratelimit.RateLimitProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.*;

import java.util.List;

@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class WebConfig implements WebMvcConfigurer {

    private final AuthService authService;
    private final RateLimitInterceptor rateLimitInterceptor;

    public WebConfig(AuthService authService, RateLimitInterceptor rateLimitInterceptor) {
        this.authService = authService;
        this.rateLimitInterceptor = rateLimitInterceptor;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new CurrentUserArgumentResolver(authService));
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/v1/**");
    }
}
