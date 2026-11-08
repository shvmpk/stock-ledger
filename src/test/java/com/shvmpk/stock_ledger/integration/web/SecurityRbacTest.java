package com.shvmpk.stock_ledger.integration.web;

import com.shvmpk.stock_ledger.config.security.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SecurityRbacTest.SecurityProbeController.class)
@Import({SecurityConfig.class, SecurityRbacTest.SecurityProbeController.class})
@DisplayName("Security & RBAC Enforcement Tests")
class SecurityRbacTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Unauthenticated request to protected endpoint should return 401 Unauthorized")
    void shouldRejectUnauthenticatedRequest() throws Exception {
        mockMvc.perform(get("/api/v1/test/operational"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "STAFF")
    @DisplayName("STAFF should be allowed to access operational endpoints")
    void shouldAllowStaffOnOperationalEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/test/operational"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "STAFF")
    @DisplayName("STAFF should be forbidden from accessing OWNER-only endpoints (403 Forbidden)")
    void shouldForbidStaffOnOwnerEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/test/owner-only"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "OWNER")
    @DisplayName("OWNER should be allowed to access OWNER-only endpoints")
    void shouldAllowOwnerOnOwnerEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/test/owner-only"))
                .andExpect(status().isOk());
    }

    @RestController
    @RequestMapping("/api/v1/test")
    static class SecurityProbeController {

        @GetMapping("/operational")
        @PreAuthorize("hasAnyRole('OWNER', 'STAFF')")
        public String operational() {
            return "operational-ok";
        }

        @GetMapping("/owner-only")
        @PreAuthorize("hasRole('OWNER')")
        public String ownerOnly() {
            return "owner-only-ok";
        }
    }
}
