package com.shvmpk.stock_ledger.platform.security;

import com.shvmpk.stock_ledger.platform.entity.PlatformCredentials;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class PlatformCredentialsCodec {

    private final ObjectMapper objectMapper;

    public String encode(PlatformCredentials credentials) {
        if (credentials == null) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(credentials);
        } catch (Exception ex) {
            throw new PlatformCredentialException("Platform credential encoding failed.");
        }
    }

    public PlatformCredentials decode(String document) {
        if (document == null || document.isBlank()) {
            return PlatformCredentials.empty();
        }
        try {
            return objectMapper.readValue(document, PlatformCredentials.class);
        } catch (Exception ex) {
            throw new PlatformCredentialException("Platform credential could not be decoded.");
        }
    }
}
