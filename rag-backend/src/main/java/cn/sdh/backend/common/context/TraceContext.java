package cn.sdh.backend.common.context;

import org.slf4j.MDC;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

public final class TraceContext {
    public static final String MDC_KEY = "traceId";
    private static final Pattern TRACE_ID = Pattern.compile("[0-9a-f]{32}");
    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private TraceContext() {}

    public static String current() {
        return CURRENT.get();
    }

    public static String currentOrCreate() {
        String current = current();
        return current == null ? generate() : current;
    }

    public static String normalize(String candidate) {
        if (candidate == null) return null;
        String normalized = candidate.trim().toLowerCase(Locale.ROOT);
        if (!TRACE_ID.matcher(normalized).matches() || "00000000000000000000000000000000".equals(normalized)) {
            return null;
        }
        return normalized;
    }

    public static String normalizeOrCreate(String candidate) {
        String normalized = normalize(candidate);
        return normalized == null ? generate() : normalized;
    }

    public static Scope open(String traceId) {
        String previous = current();
        set(normalizeOrCreate(traceId));
        return new Scope(previous);
    }

    public static void clear() {
        CURRENT.remove();
        MDC.remove(MDC_KEY);
    }

    private static void set(String traceId) {
        CURRENT.set(traceId);
        MDC.put(MDC_KEY, traceId);
    }

    private static String generate() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static final class Scope implements AutoCloseable {
        private final String previous;
        private boolean closed;

        private Scope(String previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            if (previous == null) clear();
            else set(previous);
        }
    }
}
