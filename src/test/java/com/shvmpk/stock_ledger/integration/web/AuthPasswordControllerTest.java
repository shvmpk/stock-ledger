package com.shvmpk.stock_ledger.integration.web;

import com.shvmpk.stock_ledger.TestcontainersConfiguration;
import com.shvmpk.stock_ledger.config.security.PasswordVerificationWindow;
import com.shvmpk.stock_ledger.support.AuthEventTestSupport;
import com.shvmpk.stock_ledger.user.entity.AuthEvent;
import com.shvmpk.stock_ledger.user.entity.AuthEventType;
import com.shvmpk.stock_ledger.user.entity.Role;
import com.shvmpk.stock_ledger.user.entity.User;
import com.shvmpk.stock_ledger.user.repository.AuthEventRepository;
import com.shvmpk.stock_ledger.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
@DisplayName("Self-service Password Change Tests")
class AuthPasswordControllerTest {

    private static final String OWNER_EMAIL = "ingrid.bergstrom@nordicliving.se";
    private static final String STAFF_EMAIL = "lars.eriksson@nordicliving.se";
    private static final String OWNER_PASSWORD = "FjordLantern-2026!";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthEventRepository authEventRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    private MockMvc mockMvc;
    private User owner;
    private User staff;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        AuthEventTestSupport.clear(jdbcTemplate);
        userRepository.deleteAll();

        owner = userRepository.saveAndFlush(User.create(
                UUID.randomUUID(),
                OWNER_EMAIL,
                passwordEncoder.encode(OWNER_PASSWORD),
                "Ingrid Bergström",
                Role.OWNER
        ));
        staff = userRepository.saveAndFlush(User.create(
                UUID.randomUUID(),
                STAFF_EMAIL,
                passwordEncoder.encode("PackingDesk2026!"),
                "Lars Eriksson",
                Role.STAFF
        ));
    }

    @Test
    @DisplayName("Rejects a wrong current password with INVALID_CURRENT_PASSWORD and no audit event")
    void shouldRejectWrongCurrentPassword() throws Exception {
        MockHttpSession session = login(OWNER_EMAIL, OWNER_PASSWORD);
        entityManager.flush();
        AuthEventTestSupport.clear(jdbcTemplate);

        mockMvc.perform(post("/api/v1/auth/password")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(changePayload("WrongCurrent2026!", "HarborLantern-2026")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CURRENT_PASSWORD"))
                .andExpect(jsonPath("$.invalidParams").isEmpty());

        assertThat(authEventRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("Returns every policy violation for a weak new password")
    void shouldReturnEveryPolicyViolation() throws Exception {
        MockHttpSession session = login(OWNER_EMAIL, OWNER_PASSWORD);

        mockMvc.perform(post("/api/v1/auth/password")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(changePayload(OWNER_PASSWORD, "password ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WEAK_PASSWORD"))
                .andExpect(jsonPath("$.title").value("Weak Password"))
                .andExpect(jsonPath("$.invalidParams.length()").value(3))
                .andExpect(jsonPath("$.invalidParams[0].field").value("newPassword"))
                .andExpect(jsonPath("$.invalidParams[0].message").value("Password must be at least 12 characters."))
                .andExpect(jsonPath("$.invalidParams[1].message")
                        .value("Password is too common. Choose a less predictable password."))
                .andExpect(jsonPath("$.invalidParams[2].message")
                        .value("Password must not start or end with a space."));
    }

    @Test
    @DisplayName("Rejects reusing the current password with PASSWORD_REUSE")
    void shouldRejectPasswordReuse() throws Exception {
        MockHttpSession session = login(OWNER_EMAIL, OWNER_PASSWORD);

        mockMvc.perform(post("/api/v1/auth/password")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(changePayload(OWNER_PASSWORD, OWNER_PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_REUSE"));
    }

    @Test
    @DisplayName("Changes the password, clears the flag, rehashes, and records PASSWORD_CHANGED")
    void shouldChangePasswordAndRecordAudit() throws Exception {
        MockHttpSession session = login(OWNER_EMAIL, OWNER_PASSWORD);
        String newPassword = "HarborLantern-2026!";

        mockMvc.perform(post("/api/v1/auth/password")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(changePayload(OWNER_PASSWORD, newPassword)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.mustChangePassword").value(false));

        entityManager.flush();
        entityManager.clear();
        User reloaded = userRepository.findById(owner.getId()).orElseThrow();
        assertThat(passwordEncoder.matches(OWNER_PASSWORD, reloaded.getPasswordHash())).isFalse();
        assertThat(passwordEncoder.matches(newPassword, reloaded.getPasswordHash())).isTrue();
        assertThat(reloaded.getPasswordHash()).startsWith("$2");

        List<AuthEvent> events = authEventRepository.findAll().stream()
                .filter(event -> event.getEventType() == AuthEventType.PASSWORD_CHANGED)
                .toList();
        assertThat(events).hasSize(1);
        assertThat(events.getFirst().getActorUserId()).isEqualTo(owner.getId());
        assertThat(events.getFirst().getSubjectUserId()).isEqualTo(owner.getId());
    }

    @Test
    @DisplayName("Lets STAFF change their own password")
    void shouldAllowStaffToChangeOwnPassword() throws Exception {
        MockHttpSession session = login(STAFF_EMAIL, "PackingDesk2026!");

        mockMvc.perform(post("/api/v1/auth/password")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(changePayload("PackingDesk2026!", "WarehouseDawn-2026")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mustChangePassword").value(false));
    }

    @Test
    @DisplayName("Requires authentication")
    void shouldRejectUnauthenticatedChange() throws Exception {
        mockMvc.perform(post("/api/v1/auth/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(changePayload(OWNER_PASSWORD, "HarborLantern-2026!")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Accepts a forced change without the temporary password inside the 10-minute window")
    void shouldAcceptForcedChangeWithinTheWindow() throws Exception {
        User temporary = userRepository.saveAndFlush(User.createTemporary(
                UUID.randomUUID(),
                "astrid.lindqvist@nordicliving.se",
                passwordEncoder.encode("TemporaryPass2026!"),
                "Astrid Lindqvist",
                Role.STAFF
        ));
        MockHttpSession session = login("astrid.lindqvist@nordicliving.se", "TemporaryPass2026!");

        mockMvc.perform(post("/api/v1/auth/password")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(changePayload(null, "HarborLantern-2026!")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mustChangePassword").value(false));

        entityManager.flush();
        entityManager.clear();
        assertThat(userRepository.findById(temporary.getId()).orElseThrow().isMustChangePassword()).isFalse();
    }

    @Test
    @DisplayName("Requires the current password once the forced-change window has elapsed")
    void shouldRequireCurrentPasswordAfterTheWindow() throws Exception {
        userRepository.saveAndFlush(User.createTemporary(
                UUID.randomUUID(),
                "astrid.lindqvist@nordicliving.se",
                passwordEncoder.encode("TemporaryPass2026!"),
                "Astrid Lindqvist",
                Role.STAFF
        ));
        MockHttpSession session = login("astrid.lindqvist@nordicliving.se", "TemporaryPass2026!");
        session.setAttribute(
                PasswordVerificationWindow.SESSION_ATTRIBUTE,
                Instant.now().minus(Duration.ofMinutes(11))
        );

        mockMvc.perform(post("/api/v1/auth/password")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(changePayload(null, "HarborLantern-2026!")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_REQUIRED"));

        mockMvc.perform(post("/api/v1/auth/password")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(changePayload("TemporaryPass2026!", "HarborLantern-2026!")))
                .andExpect(status().isOk());
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

        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private String changePayload(String currentPassword, String newPassword) {
        if (currentPassword == null) {
            return """
                    {
                        "newPassword": "%s"
                    }
                    """.formatted(newPassword);
        }
        return """
                {
                    "currentPassword": "%s",
                    "newPassword": "%s"
                }
                """.formatted(currentPassword, newPassword);
    }
}
