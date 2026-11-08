package com.shvmpk.stock_ledger.config.security;

import com.shvmpk.stock_ledger.user.repository.UserRepository;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PasswordSecurityConfig {

    @Bean
    public MustChangePasswordFilter mustChangePasswordFilter(
            UserRepository userRepository,
            ProblemDetailWriter problemDetailWriter
    ) {
        return new MustChangePasswordFilter(userRepository, problemDetailWriter);
    }

    /**
     * The filter is installed inside the Spring Security chain, after
     * authentication. Container auto-registration would run it before
     * SecurityContextHolder is populated, so it must be disabled.
     */
    @Bean
    public FilterRegistrationBean<MustChangePasswordFilter> mustChangePasswordFilterRegistration(
            MustChangePasswordFilter mustChangePasswordFilter
    ) {
        FilterRegistrationBean<MustChangePasswordFilter> registration =
                new FilterRegistrationBean<>(mustChangePasswordFilter);
        registration.setEnabled(false);
        return registration;
    }
}
