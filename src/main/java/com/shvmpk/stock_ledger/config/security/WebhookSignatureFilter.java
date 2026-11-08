package com.shvmpk.stock_ledger.config.security;

import com.shvmpk.stock_ledger.platform.entity.PlatformConnection;
import com.shvmpk.stock_ledger.platform.entity.PlatformType;
import com.shvmpk.stock_ledger.platform.repository.PlatformConnectionRepository;
import com.shvmpk.stock_ledger.platform.security.PlatformCredentialCipher;
import com.shvmpk.stock_ledger.platform.security.PlatformCredentialException;
import com.shvmpk.stock_ledger.platform.service.HmacSignatureValidator;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Verifies inbound order webhooks against the webhook secret of the single
 * active connection for the platform in the URL. The request body is cached so
 * the HMAC is computed over the exact bytes sent by the platform.
 */
@Slf4j
public class WebhookSignatureFilter extends OncePerRequestFilter {

    public static final String REQUEST_HASH_ATTRIBUTE = "stockledger.webhook.requestHash";
    public static final String CONNECTION_ID_ATTRIBUTE = "stockledger.webhook.connectionId";

    private static final Pattern WEBHOOK_PATH = Pattern.compile("^/api/v1/webhooks/orders/[^/]+$");
    private static final String WEBHOOK_SECRET_AAD = "webhookSecret";
    private static final String INVALID_SIGNATURE_CODE = "INVALID_WEBHOOK_SIGNATURE";
    private static final String INVALID_SIGNATURE_TYPE =
            "https://api.stockledger.io/errors/invalid-webhook-signature";
    private static final String INVALID_SIGNATURE_DETAIL = "Webhook signature verification failed.";

    private final PlatformConnectionRepository platformConnectionRepository;
    private final PlatformCredentialCipher credentialCipher;
    private final HmacSignatureValidator hmacSignatureValidator;
    private final ObjectMapper objectMapper;

    public WebhookSignatureFilter(
            PlatformConnectionRepository platformConnectionRepository,
            PlatformCredentialCipher credentialCipher,
            HmacSignatureValidator hmacSignatureValidator,
            ObjectMapper objectMapper
    ) {
        this.platformConnectionRepository = platformConnectionRepository;
        this.credentialCipher = credentialCipher;
        this.hmacSignatureValidator = hmacSignatureValidator;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !WEBHOOK_PATH.matcher(path).matches();
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        CachedBodyHttpServletRequest cachedRequest = new CachedBodyHttpServletRequest(request);
        String platformName = platformNameFrom(cachedRequest);
        String slug = platformName.toLowerCase(Locale.ROOT);

        Optional<PlatformConnection> activeConnection =
                platformConnectionRepository.findBySlugAndActiveTrue(slug);

        if (activeConnection.isEmpty()) {
            activeConnection = singleActiveConnectionFor(platformName);
        }

        if (activeConnection.isEmpty() || !activeConnection.get().isWebhookConfigured()) {
            log.warn("Rejected webhook for '{}': no active webhook-configured connection.", platformName);
            reject(response);
            return;
        }

        String secret;
        try {
            secret = credentialCipher.decrypt(
                    activeConnection.get().getWebhookSecretEncrypted(),
                    WEBHOOK_SECRET_AAD
            );
        } catch (PlatformCredentialException ex) {
            log.warn(
                    "Rejected webhook for '{}': webhook secret could not be decrypted for connection {}.",
                    platformName,
                    activeConnection.get().getId()
            );
            reject(response);
            return;
        }

        if (secret == null || secret.isBlank()) {
            log.warn("Rejected webhook for '{}': webhook secret is empty.", platformName);
            reject(response);
            return;
        }

        String signature = firstSignatureHeader(cachedRequest, activeConnection.get().getPlatformType());

        if (!hmacSignatureValidator.isValid(cachedRequest.getBody(), secret, signature)) {
            log.warn("Rejected webhook for '{}': HMAC signature is missing or invalid.", platformName);
            reject(response);
            return;
        }

        cachedRequest.setAttribute(
                REQUEST_HASH_ATTRIBUTE,
                WebhookRequestHasher.sha256Hex(cachedRequest.getBody())
        );
        cachedRequest.setAttribute(
                CONNECTION_ID_ATTRIBUTE,
                activeConnection.get().getId()
        );
        filterChain.doFilter(cachedRequest, response);
    }

    private String platformNameFrom(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private PlatformType resolvePlatformType(String platformName) {
        try {
            return PlatformType.valueOf(platformName.toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /**
     * Tolerance for older webhook URLs that name the platform instead of the connection slug.
     * It only resolves when exactly one active connection of that type exists; with several
     * stores on one platform the request is ambiguous and is refused rather than guessed.
     */
    private Optional<PlatformConnection> singleActiveConnectionFor(String platformName) {
        PlatformType platformType = resolvePlatformType(platformName);
        if (platformType == null) {
            return Optional.empty();
        }
        List<PlatformConnection> candidates =
                platformConnectionRepository.findByPlatformTypeAndActiveTrue(platformType);
        if (candidates.size() == 1) {
            return Optional.of(candidates.get(0));
        }
        if (candidates.size() > 1) {
            log.warn(
                    "Rejected webhook for '{}': {} active {} connections exist and the address names none of them.",
                    platformName,
                    candidates.size(),
                    platformType
            );
        }
        return Optional.empty();
    }

    private String firstSignatureHeader(HttpServletRequest request, PlatformType platformType) {
        for (String headerName : platformType.signatureHeaders()) {
            String value = request.getHeader(headerName);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private void reject(HttpServletResponse response) throws IOException {
        Map<String, Object> problem = new LinkedHashMap<>();
        problem.put("type", INVALID_SIGNATURE_TYPE);
        problem.put("title", "Unauthorized");
        problem.put("status", HttpStatus.UNAUTHORIZED.value());
        problem.put("detail", INVALID_SIGNATURE_DETAIL);
        problem.put("code", INVALID_SIGNATURE_CODE);
        problem.put("timestamp", Instant.now().toString());
        problem.put("invalidParams", List.of());

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(problem));
        response.getWriter().flush();
    }
}
