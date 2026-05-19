package pl.jakubtworek.backend_systems_lab_stage_1.block_c.mvc;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
        System.out.println(
                "Incoming request: " + request.getMethod() + " " + request.getRequestURI()
        );

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
        System.out.println("Controller executed successfully");
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
        System.out.println("Request completed with status: " + response.getStatus());
    }
}