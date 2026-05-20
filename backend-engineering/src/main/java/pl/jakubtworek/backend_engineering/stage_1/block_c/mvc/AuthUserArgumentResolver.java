package pl.jakubtworek.backend_engineering.stage_1.block_c.mvc;

import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Custom HandlerMethodArgumentResolver.
 *
 * It allows injecting AuthUser directly into controller methods.
 *
 * This is part of Spring MVC argument resolution pipeline.
 */
@Component
public class AuthUserArgumentResolver implements HandlerMethodArgumentResolver {

    /**
     * Tells Spring MVC which method parameters are supported
     * by this resolver.
     */
    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType().equals(AuthUser.class);
    }

    /**
     * Creates value for supported controller method parameter.
     *
     * In this example username is read from Spring Security context.
     */
    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            org.springframework.web.bind.support.WebDataBinderFactory binderFactory
    ) {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            return new AuthUser("anonymous");
        }

        return new AuthUser(authentication.getName());
    }
}