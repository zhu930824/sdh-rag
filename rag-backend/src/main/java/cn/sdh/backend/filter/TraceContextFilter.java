package cn.sdh.backend.filter;

import cn.sdh.backend.common.context.TraceContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceContextFilter extends OncePerRequestFilter {
    public static final String TRACE_HEADER = "X-Trace-Id";
    private static final Pattern TRACE_PARENT = Pattern.compile(
            "^([0-9a-fA-F]{2})-([0-9a-fA-F]{32})-([0-9a-fA-F]{16})-([0-9a-fA-F]{2})$");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = resolveIncoming(request);
        try (TraceContext.Scope ignored = TraceContext.open(traceId)) {
            response.setHeader(TRACE_HEADER, TraceContext.current());
            filterChain.doFilter(request, response);
        }
    }

    private String resolveIncoming(HttpServletRequest request) {
        String traceParent = request.getHeader("traceparent");
        if (traceParent != null) {
            Matcher matcher = TRACE_PARENT.matcher(traceParent.trim());
            if (matcher.matches() && !"ff".equalsIgnoreCase(matcher.group(1))
                    && !"0000000000000000".equals(matcher.group(3))) {
                String traceId = TraceContext.normalize(matcher.group(2));
                if (traceId != null) return traceId;
            }
        }
        return TraceContext.normalize(request.getHeader(TRACE_HEADER));
    }
}
