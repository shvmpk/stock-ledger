package com.shvmpk.stock_ledger.unit.security;

import com.shvmpk.stock_ledger.config.security.RequestIdFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("RequestIdFilter Tests")
class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    @DisplayName("Echoes a valid inbound request id unchanged")
    void shouldEchoValidInboundId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/stock/movements");
        request.addHeader("X-Request-Id", "req_0123456789abcdef");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> mdcDuringChain = new AtomicReference<>();

        filter.doFilter(request, response, captureMdc(mdcDuringChain));

        assertThat(response.getHeader("X-Request-Id")).isEqualTo("req_0123456789abcdef");
        assertThat(mdcDuringChain).hasValue("req_0123456789abcdef");
    }

    @Test
    @DisplayName("Generates a req_ hex id when the header is missing")
    void shouldGenerateIdWhenMissing() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest("GET", "/api/v1/stock/movements"), response, (req, res) -> {
        });

        assertThat(response.getHeader("X-Request-Id")).matches("req_[0-9a-f]{32}");
    }

    @Test
    @DisplayName("Replaces a malformed inbound id")
    void shouldReplaceMalformedId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/stock/movements");
        request.addHeader("X-Request-Id", "not a request id!");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
        });

        assertThat(response.getHeader("X-Request-Id"))
                .matches("req_[0-9a-f]{32}")
                .isNotEqualTo("not a request id!");
    }

    @Test
    @DisplayName("Clears the MDC after the chain completes, even when it throws")
    void shouldClearMdcAfterChain() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/stock/movements");
        request.addHeader("X-Request-Id", "req_0123456789abcdef");

        assertThatThrownBy(() -> filter.doFilter(
                request,
                new MockHttpServletResponse(),
                (req, res) -> {
                    throw new ServletException("Downstream failure");
                }
        )).isInstanceOf(ServletException.class);

        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    @DisplayName("Runs before the Spring Security filter chain")
    void shouldRunBeforeSpringSecurity() {
        Order order = RequestIdFilter.class.getAnnotation(Order.class);

        assertThat(order).isNotNull();
        assertThat(order.value()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
    }

    private FilterChain captureMdc(AtomicReference<String> duringChain) {
        return (request, response) -> duringChain.set(MDC.get("requestId"));
    }
}
