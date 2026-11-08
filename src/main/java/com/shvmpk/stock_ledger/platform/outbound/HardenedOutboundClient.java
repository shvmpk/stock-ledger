package com.shvmpk.stock_ledger.platform.outbound;

import jakarta.annotation.PreDestroy;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The only way the application talks to a merchant's sales channel.
 *
 * <p>Every call is checked by {@link OutboundAddressPolicy} before a socket is
 * opened, uses {@link ValidatingDnsResolver} so the checked address is the one
 * connected to, refuses redirects, caps the response body, and applies both a
 * connect and a read timeout.</p>
 */
@Component
public class HardenedOutboundClient {

    private final OutboundAddressPolicy policy;
    private final CloseableHttpClient client;
    private final int maxResponseBytes;

    public HardenedOutboundClient(
            OutboundAddressPolicy policy,
            ValidatingDnsResolver resolver,
            @Value("${app.platform.sync.http-timeout:10s}") Duration connectTimeout,
            @Value("${app.platform.outbound.max-response-bytes:1048576}") int maxResponseBytes
    ) {
        this.policy = policy;
        this.maxResponseBytes = maxResponseBytes;

        ConnectionConfig connectionConfig = ConnectionConfig.custom()
                .setConnectTimeout(Timeout.ofMilliseconds(connectTimeout.toMillis()))
                .build();

        PoolingHttpClientConnectionManager connectionManager =
                PoolingHttpClientConnectionManagerBuilder.create()
                        .setDnsResolver(resolver)
                        .setDefaultConnectionConfig(connectionConfig)
                        .build();

        this.client = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .disableRedirectHandling()
                .disableAutomaticRetries()
                .build();
    }

    public OutboundResponse execute(OutboundRequest request) {
        policy.validateScheme(request.uri());

        HttpGet get = new HttpGet(request.uri());
        request.headers().forEach((name, values) ->
                values.forEach(value -> get.addHeader(name, value)));
        get.setConfig(RequestConfig.custom()
                .setResponseTimeout(Timeout.ofMilliseconds(request.timeout().toMillis()))
                .setConnectTimeout(Timeout.ofMilliseconds(request.timeout().toMillis()))
                .setRedirectsEnabled(false)
                .build());

        try (CloseableHttpResponse response = client.execute(get)) {
            int statusCode = response.getCode();
            if (statusCode >= 300 && statusCode < 400) {
                throw new PlatformOutboundException(
                        "Platform redirected the request to another address; redirects are not followed."
                );
            }
            return new OutboundResponse(
                    statusCode,
                    headers(response),
                    readBody(response.getEntity())
            );
        } catch (PlatformOutboundException ex) {
            throw ex;
        } catch (InterruptedIOException ex) {
            throw new PlatformOutboundException(
                    "Platform request timed out while waiting for an answer.",
                    ex
            );
        } catch (IOException ex) {
            PlatformOutboundException blocked = findBlocked(ex);
            if (blocked != null) {
                throw blocked;
            }
            throw new PlatformOutboundException("Platform could not be reached.", ex);
        }
    }

    @PreDestroy
    void close() throws IOException {
        client.close();
    }

    private Map<String, List<String>> headers(CloseableHttpResponse response) {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        for (Header header : response.getHeaders()) {
            headers.computeIfAbsent(header.getName(), ignored -> new ArrayList<>())
                    .add(header.getValue());
        }
        return headers;
    }

    private String readBody(HttpEntity entity) throws IOException {
        if (entity == null) {
            return "";
        }

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (InputStream input = entity.getContent()) {
            byte[] chunk = new byte[8192];
            int read;
            while ((read = input.read(chunk)) != -1) {
                if (buffer.size() + read > maxResponseBytes) {
                    throw new PlatformOutboundException(
                            "Platform response was too large and was refused."
                    );
                }
                buffer.write(chunk, 0, read);
            }
        }

        ContentType contentType = ContentType.parseLenient(entity.getContentType());
        Charset charset = contentType != null && contentType.getCharset() != null
                ? contentType.getCharset()
                : StandardCharsets.UTF_8;
        return buffer.toString(charset);
    }

    private PlatformOutboundException findBlocked(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof PlatformOutboundException outbound) {
                return outbound;
            }
            current = current.getCause();
        }
        return null;
    }
}
