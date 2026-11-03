package cn.sdh.backend.filter;

import cn.sdh.backend.common.context.TraceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class TraceContextFilterTest {
    private final TraceContextFilter filter = new TraceContextFilter();

    @AfterEach
    void cleanup() {
        TraceContext.clear();
    }

    @Test
    void acceptsW3cTraceParentAndCleansThreadContext() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("traceparent", "00-0123456789abcdef0123456789abcdef-0123456789abcdef-01");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> duringRequest = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> duringRequest.set(TraceContext.current()));

        assertEquals("0123456789abcdef0123456789abcdef", duringRequest.get());
        assertEquals(duringRequest.get(), response.getHeader(TraceContextFilter.TRACE_HEADER));
        assertNull(TraceContext.current());
    }

    @Test
    void rejectsMalformedCallerTraceAndGeneratesSafeId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TraceContextFilter.TRACE_HEADER, "../../forged");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {});

        String generated = response.getHeader(TraceContextFilter.TRACE_HEADER);
        assertNotNull(generated);
        assertTrue(generated.matches("[0-9a-f]{32}"));
        assertNotEquals("../../forged", generated);
    }
}
