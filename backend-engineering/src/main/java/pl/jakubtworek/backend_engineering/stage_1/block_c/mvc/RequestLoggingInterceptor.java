package pl.jakubtworek.backend_engineering.stage_1.block_c.mvc;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * HandlerInterceptor works inside Spring MVC pipeline.
 *
 * It is different from servlet Filter:
 * - Filter runs before DispatcherServlet,
 * - HandlerInterceptor runs after DispatcherServlet chooses handler.
 */
@Component
public class RequestLoggingInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingInterceptor.class);
    private static final String START_NANOS_ATTRIBUTE =
            RequestLoggingInterceptor.class.getName() + ".startNanos";

    /**
     * Executed before controller method.
     *
     * Returning false stops request processing.
     */
    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler
    ) {
        request.setAttribute(START_NANOS_ATTRIBUTE, System.nanoTime());
        log.debug("HTTP request started method={} path={}",
                request.getMethod(), request.getRequestURI());

        return true;
    }

    /**
     * Executed after controller method,
     * before response is rendered.
     *
     * For @RestController, response body conversion may happen later.
     */
    @Override
    public void postHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            org.springframework.web.servlet.ModelAndView modelAndView
    ) {
        // Response body conversion can still fail after postHandle, so the final
        // status and duration are logged in afterCompletion.
    }

    /**
     * Executed after complete request processing.
     *
     * Useful for cleanup and logging.
     */
    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception exception
    ) {
        Object start = request.getAttribute(START_NANOS_ATTRIBUTE);
        long durationMillis = start instanceof Long startNanos
                ? (System.nanoTime() - startNanos) / 1_000_000
                : -1;
        String outcome = response.getStatus() >= 500
                ? "server_error"
                : response.getStatus() >= 400 ? "client_error" : "success";
        log.info("HTTP request completed method={} path={} status={} durationMs={} outcome={}",
                request.getMethod(),
                request.getRequestURI(),
                response.getStatus(),
                durationMillis,
                exception == null ? outcome : "unhandled_error");
    }
}
