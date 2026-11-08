package com.shvmpk.stock_ledger.integration.web;

import com.shvmpk.stock_ledger.config.security.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CorsConfigurationTest.CorsProbeController.class)
@Import({SecurityConfig.class, CorsConfigurationTest.CorsProbeController.class})
@DisplayName("CORS Integration Configuration Slice Test")
class CorsConfigurationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Preflight OPTIONS request from allowed frontend origin (localhost:5173) should return CORS headers")
    void shouldAllowFrontendOriginOnPreflight() throws Exception {
        mockMvc.perform(options("/api/v1/probe")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    @DisplayName("Preflight request from disallowed origin should not return Access-Control-Allow-Origin")
    void shouldNotAllowDisallowedOrigin() throws Exception {
        mockMvc.perform(options("/api/v1/probe")
                        .header("Origin", "http://malicious-site.com")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @RestController
    @RequestMapping("/api/v1/probe")
    static class CorsProbeController {
        @GetMapping
        public String probe() {
            return "ok";
        }
    }
}
