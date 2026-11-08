package com.shvmpk.stock_ledger.config.security;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Pattern;

@Component
public class RequestMetadataResolver {

    private static final Pattern REQUEST_ID_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{8,64}$");
    private static final String REQUEST_ID_MDC_KEY = "requestId";

    private final boolean trustForwardedHeaders;

    public RequestMetadataResolver(
            @Value("${app.security.trust-forwarded-headers:false}") boolean trustForwardedHeaders
    ) {
        this.trustForwardedHeaders = trustForwardedHeaders;
    }

    public HttpRequestMetadata resolveCurrent() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            return resolve(servletAttributes.getRequest());
        }
        return HttpRequestMetadata.empty();
    }

    public HttpRequestMetadata resolve(HttpServletRequest request) {
        return new HttpRequestMetadata(
                resolveAddress(request),
                blankToNull(request.getHeader("User-Agent")),
                resolveRequestId(request)
        );
    }

    private InetAddress resolveAddress(HttpServletRequest request) {
        if (trustForwardedHeaders) {
            String forwardedFor = request.getHeader("X-Forwarded-For");
            if (forwardedFor != null && !forwardedFor.isBlank()) {
                InetAddress forwarded = parseAddress(forwardedFor.split(",")[0]);
                if (forwarded != null) {
                    return forwarded;
                }
            }
        }
        return parseAddress(request.getRemoteAddr());
    }

    private String resolveRequestId(HttpServletRequest request) {
        String fromMdc = MDC.get(REQUEST_ID_MDC_KEY);
        if (isValidRequestId(fromMdc)) {
            return fromMdc;
        }
        String fromHeader = request.getHeader("X-Request-Id");
        return isValidRequestId(fromHeader) ? fromHeader : null;
    }

    private boolean isValidRequestId(String value) {
        return value != null && REQUEST_ID_PATTERN.matcher(value).matches();
    }

    private InetAddress parseAddress(String candidate) {
        if (candidate == null) {
            return null;
        }
        String value = candidate.trim();
        if (value.isEmpty() || (!value.contains(".") && !value.contains(":"))) {
            return null;
        }
        try {
            return InetAddress.getByName(value);
        } catch (UnknownHostException exception) {
            return null;
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
