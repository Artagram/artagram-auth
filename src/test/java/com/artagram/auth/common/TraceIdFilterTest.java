package com.artagram.auth.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TraceIdFilterTest {

    private final TraceIdFilter filtro = new TraceIdFilter();

    private String ejecutar(String traceRecibido) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/google");
        if (traceRecibido != null) {
            request.addHeader(TraceIdFilter.TRACE_ID_HEADER, traceRecibido);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> enMdc = new AtomicReference<>();

        filtro.doFilter(request, response, (req, res) -> enMdc.set(MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY)));

        assertThat(MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY)).isNull();
        assertThat(response.getHeader(TraceIdFilter.TRACE_ID_HEADER)).isEqualTo(enMdc.get());
        return enMdc.get();
    }

    @Test
    void conservaElTraceIdValidoYGeneraUnoSiFaltaOEsDemasiadoLargo() throws Exception {
        assertThat(ejecutar("trace-123")).isEqualTo("trace-123");
        assertThat(ejecutar(null)).hasSize(36);
        assertThat(ejecutar("  ")).hasSize(36);
        assertThat(ejecutar("x".repeat(65))).hasSize(36);
    }
}
