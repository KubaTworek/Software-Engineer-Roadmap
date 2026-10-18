package pl.jakubtworek.backend.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

/** Ustanawia lub skraca deadline; downstream nie może rozszerzyć budżetu klienta. */
public class DeadlineFilter extends OncePerRequestFilter {
    private static final long DEFAULT_BUDGET_MS = 5_000;
    private static final long MAX_ACCEPTED_BUDGET_MS = 10_000;

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                              FilterChain chain) throws ServletException, IOException {
        long now = System.currentTimeMillis();
        long requested = parse(request.getHeader(DeadlineContext.HEADER), now + DEFAULT_BUDGET_MS);
        long deadline = Math.min(requested, now + MAX_ACCEPTED_BUDGET_MS);
        if (deadline <= now) {
            response.sendError(504, "Request deadline already exceeded");
            return;
        }
        DeadlineContext.set(deadline);
        response.setHeader(DeadlineContext.HEADER, Long.toString(deadline));
        try { chain.doFilter(request, response); } finally { DeadlineContext.clear(); }
    }

    private long parse(String value, long fallback) {
        try { return value == null ? fallback : Long.parseLong(value); }
        catch (NumberFormatException ignored) { return fallback; }
    }
}
