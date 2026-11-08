package com.shvmpk.stock_ledger.integration.web;

import com.shvmpk.stock_ledger.TestcontainersConfiguration;
import com.shvmpk.stock_ledger.support.AuthEventTestSupport;
import com.shvmpk.stock_ledger.user.entity.Role;
import com.shvmpk.stock_ledger.user.entity.User;
import com.shvmpk.stock_ledger.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
@DisplayName("Forced Password Change Enforcement Tests")
class MustChangePasswordEnforcementTest {

    private static final String FLAGGED_EMAIL = "astrid.lindqvist@nordicliving.se";
    private static final String FLAGGED_PASSWORD = "TemporaryPass2026!";
    private static final String OWNER_EMAIL = "ingrid.bergstrom@nordicliving.se";
    private static final String OWNER_PASSWORD = "FjordLantern-2026!";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        AuthEventTestSupport.clear(jdbcTemplate);
        userRepository.deleteAll();

        userRepository.saveAndFlush(User.createTemporary(
                UUID.randomUUID(),
                FLAGGED_EMAIL,
                passwordEncoder.encode(FLAGGED_PASSWORD),
                "Astrid Lindqvist",
                Role.STAFF
        ));
        userRepository.saveAndFlush(User.create(
                UUID.randomUUID(),
                OWNER_EMAIL,
                passwordEncoder.encode(OWNER_PASSWORD),
                "Ingrid Bergström",
                Role.OWNER
        ));
    }

    @Test
    @DisplayName("Blocks protected endpoints with an RFC 7807 PASSWORD_CHANGE_REQUIRED problem")
    void shouldBlockProtectedEndpoints() throws Exception {
        MockHttpSession session = login(FLAGGED_EMAIL, FLAGGED_PASSWORD);

        mockMvc.perform(get("/api/v1/stock/movements").session(session))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"))
                .andExpect(jsonPath("$.title").value("Password Change Required"))
                .andExpect(jsonPath("$.detail")
                        .value("Your password needs to be updated before you can continue."));
    }

    @Test
    @DisplayName("Allows the change endpoint, /auth/me, and logout while the flag is set")
    void shouldAllowAccountEndpointsWhileFlagged() throws Exception {
        MockHttpSession session = login(FLAGGED_EMAIL, FLAGGED_PASSWORD);

        mockMvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mustChangePassword").value(true));

        mockMvc.perform(post("/api/v1/auth/password")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "newPassword": "HarborLantern-2026!"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mustChangePassword").value(false));

        mockMvc.perform(post("/api/v1/auth/logout").session(session))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Leaves health checks and inbound webhooks unaffected")
    void shouldLeavePublicEndpointsUnaffected() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/webhooks/orders/shopify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_WEBHOOK_SIGNATURE"));
    }

    @Test
    @DisplayName("Unblocks the same session immediately after a successful change")
    void shouldUnblockSameSessionAfterChange() throws Exception {
        MockHttpSession session = login(FLAGGED_EMAIL, FLAGGED_PASSWORD);

        mockMvc.perform(get("/api/v1/stock/movements").session(session))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/auth/password")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "newPassword": "HarborLantern-2026!"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/stock/movements").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("Never filters users whose password is permanent")
    void shouldNotFilterUnflaggedUsers() throws Exception {
        MockHttpSession session = login(OWNER_EMAIL, OWNER_PASSWORD);

        mockMvc.perform(get("/api/v1/stock/movements").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    private MockHttpSession login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "%s",
                                    "password": "%s"
                                }
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getRequest().getSession(false)).isInstanceOf(MockHttpSession.class);
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
