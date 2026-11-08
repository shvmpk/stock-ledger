package com.shvmpk.stock_ledger.config.security;

import com.shvmpk.stock_ledger.platform.repository.PlatformConnectionRepository;
import com.shvmpk.stock_ledger.platform.security.PlatformCredentialCipher;
import com.shvmpk.stock_ledger.platform.service.HmacSignatureValidator;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class WebhookSecurityConfig {

    @Bean
    public WebhookSignatureFilter webhookSignatureFilter(
            PlatformConnectionRepository platformConnectionRepository,
            PlatformCredentialCipher credentialCipher,
            HmacSignatureValidator hmacSignatureValidator,
            ObjectMapper objectMapper
    ) {
        return new WebhookSignatureFilter(
                platformConnectionRepository,
                credentialCipher,
                hmacSignatureValidator,
                objectMapper
        );
    }

    @Bean
    public FilterRegistrationBean<WebhookSignatureFilter> webhookSignatureFilterRegistration(
            WebhookSignatureFilter webhookSignatureFilter
    ) {
        FilterRegistrationBean<WebhookSignatureFilter> registration =
                new FilterRegistrationBean<>(webhookSignatureFilter);
        registration.setEnabled(false);
        return registration;
    }
}
