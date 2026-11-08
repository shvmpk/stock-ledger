package com.shvmpk.stock_ledger.config.security;

import com.shvmpk.stock_ledger.common.error.InvalidParam;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RFC 7807 writer for filters, which run outside Spring MVC and therefore
 * cannot rely on {@code @RestControllerAdvice}. The shape matches
 * {@code GlobalExceptionHandler} exactly.
 */
@Component
@RequiredArgsConstructor
public class ProblemDetailWriter {

    private static final String PROBLEM_BASE_URL = "https://api.stockledger.io/errors/";

    private final ObjectMapper objectMapper;

    public void write(
            HttpServletResponse response,
            HttpStatus status,
            String errorCode,
            String title,
            String detail
    ) throws IOException {
        Map<String, Object> problem = new LinkedHashMap<>();
        problem.put("type", PROBLEM_BASE_URL + errorCode.toLowerCase().replace('_', '-'));
        problem.put("title", title);
        problem.put("status", status.value());
        problem.put("detail", detail);
        problem.put("code", errorCode);
        problem.put("timestamp", Instant.now().toString());
        problem.put("invalidParams", List.<InvalidParam>of());

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
        response.getOutputStream().flush();
    }
}
