package com.shvmpk.stock_ledger.unit.security;

import com.shvmpk.stock_ledger.config.security.HttpRequestMetadata;
import com.shvmpk.stock_ledger.config.security.RequestMetadataResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RequestMetadataResolver Tests")
class RequestMetadataResolverTest {

    private RequestMetadataResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new RequestMetadataResolver(false);
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
        MDC.clear();
    }

    @Test
    @DisplayName("Uses the remote address when no forwarding header is present")
    void shouldUseRemoteAddress() {
        MockHttpServletRequest request = request("198.51.100.24");

        HttpRequestMetadata metadata = resolver.resolve(request);

        assertThat(metadata.ipAddress()).isNotNull();
        assertThat(metadata.ipAddress().getHostAddress()).isEqualTo("198.51.100.24");
    }

    @Test
    @DisplayName("Ignores X-Forwarded-For when the trust flag is disabled")
    void shouldIgnoreForwardedForWhenUntrusted() {
        MockHttpServletRequest request = request("198.51.100.24");
        request.addHeader("X-Forwarded-For", "203.0.113.99");

        HttpRequestMetadata metadata = resolver.resolve(request);

        assertThat(metadata.ipAddress().getHostAddress()).isEqualTo("198.51.100.24");
    }

    @Test
    @DisplayName("Uses the first forwarded hop when the trust flag is enabled")
    void shouldUseFirstForwardedHopWhenTrusted() {
        resolver = new RequestMetadataResolver(true);
        MockHttpServletRequest request = request("10.0.0.5");
        request.addHeader("X-Forwarded-For", "203.0.113.99, 10.0.0.5");

        HttpRequestMetadata metadata = resolver.resolve(request);

        assertThat(metadata.ipAddress().getHostAddress()).isEqualTo("203.0.113.99");
    }

    @Test
    @DisplayName("Falls back to the remote address when the forwarded value is malformed")
    void shouldFallBackForMalformedForwardedAddress() {
        resolver = new RequestMetadataResolver(true);
        MockHttpServletRequest request = request("198.51.100.24");
        request.addHeader("X-Forwarded-For", "not-an-ip");

        HttpRequestMetadata metadata = resolver.resolve(request);

        assertThat(metadata.ipAddress().getHostAddress()).isEqualTo("198.51.100.24");
    }

    @Test
    @DisplayName("Reads the request id from the MDC first")
    void shouldReadRequestIdFromMdc() {
        MDC.put("requestId", "req_4f1c2a9b8d7e6f5a4b3c2d1e0f9a8b7c");
        MockHttpServletRequest request = request("198.51.100.24");
        request.addHeader("X-Request-Id", "req_should_not_win");

        HttpRequestMetadata metadata = resolver.resolve(request);

        assertThat(metadata.requestId()).isEqualTo("req_4f1c2a9b8d7e6f5a4b3c2d1e0f9a8b7c");
    }

    @Test
    @DisplayName("Accepts a well-formed X-Request-Id header when the MDC is empty")
    void shouldAcceptValidRequestIdHeader() {
        MockHttpServletRequest request = request("198.51.100.24");
        request.addHeader("X-Request-Id", "req_0123456789abcdef");

        HttpRequestMetadata metadata = resolver.resolve(request);

        assertThat(metadata.requestId()).isEqualTo("req_0123456789abcdef");
    }

    @Test
    @DisplayName("Ignores a malformed X-Request-Id header")
    void shouldRejectInvalidRequestIdHeader() {
        MockHttpServletRequest request = request("198.51.100.24");
        request.addHeader("X-Request-Id", "bad request id!");

        HttpRequestMetadata metadata = resolver.resolve(request);

        assertThat(metadata.requestId()).isNull();
    }

    @Test
    @DisplayName("Resolves metadata from the current request context")
    void shouldResolveCurrentRequest() {
        MockHttpServletRequest request = request("198.51.100.24");
        request.addHeader("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) Chrome/141.0 Safari/537.36");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        HttpRequestMetadata metadata = resolver.resolveCurrent();

        assertThat(metadata.ipAddress().getHostAddress()).isEqualTo("198.51.100.24");
        assertThat(metadata.userAgent()).contains("Chrome/141.0");
    }

    @Test
    @DisplayName("Returns empty metadata outside a request context")
    void shouldReturnEmptyMetadataOutsideRequest() {
        RequestContextHolder.resetRequestAttributes();

        HttpRequestMetadata metadata = resolver.resolveCurrent();

        assertThat(metadata.ipAddress()).isNull();
        assertThat(metadata.userAgent()).isNull();
        assertThat(metadata.requestId()).isNull();
    }

    private MockHttpServletRequest request(String remoteAddress) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddress);
        return request;
    }
}
