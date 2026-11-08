package com.shvmpk.stock_ledger.integration.web;

import com.shvmpk.stock_ledger.TestcontainersConfiguration;
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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
@DisplayName("AuthController Integration Tests (Task 1)")
class AuthControllerTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        userRepository.deleteAll();
        User owner = User.create(
                UUID.randomUUID(),
                "owner@stockledger.io",
                passwordEncoder.encode("SecretPass2026!"),
                "Ingrid Bergström",
                Role.OWNER
        );
        userRepository.save(owner);
    }

    @Test
    @DisplayName("POST /api/v1/auth/login with valid credentials should authenticate and return user info")
    void shouldLoginWithValidCredentials() throws Exception {
        String payload = """
                {
                    "email": "owner@stockledger.io",
                    "password": "SecretPass2026!"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("owner@stockledger.io"))
                .andExpect(jsonPath("$.data.fullName").value("Ingrid Bergström"))
                .andExpect(jsonPath("$.data.role").value("OWNER"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login with invalid password should return 401 Unauthorized")
    void shouldRejectInvalidPassword() throws Exception {
        String payload = """
                {
                    "email": "owner@stockledger.io",
                    "password": "WrongPassword!"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/v1/auth/me should return current user when session is authenticated")
    void shouldReturnCurrentUserWhenAuthenticated() throws Exception {
        String payload = """
                {
                    "email": "owner@stockledger.io",
                    "password": "SecretPass2026!"
                }
                """;

        // 1. Login to obtain session
        MockHttpSession session = (MockHttpSession) mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andReturn().getRequest().getSession();

        // 2. Access /api/v1/auth/me with session
        mockMvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("owner@stockledger.io"))
                .andExpect(jsonPath("$.data.role").value("OWNER"));
    }

    @Test
    @DisplayName("GET /api/v1/auth/me should return 401 Unauthorized when not authenticated")
    void shouldRejectMeWhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/v1/auth/logout should invalidate session")
    void shouldLogoutAndInvalidateSession() throws Exception {
        String payload = """
                {
                    "email": "owner@stockledger.io",
                    "password": "SecretPass2026!"
                }
                """;

        MockHttpSession session = (MockHttpSession) mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andReturn().getRequest().getSession();

        mockMvc.perform(post("/api/v1/auth/logout").session(session))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(status().isUnauthorized());
    }
}
