package com.shvmpk.stock_ledger.integration.web;

import com.shvmpk.stock_ledger.TestcontainersConfiguration;
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
@DisplayName("Owner Password Reset Tests")
class UserPasswordResetControllerTest {

    private static final String OWNER_EMAIL = "ingrid.bergstrom@nordicliving.se";
    private static final String STAFF_EMAIL = "lars.eriksson@nordicliving.se";
    private static final String OWNER_PASSWORD = "FjordLantern-2026!";
    private static final String STAFF_PASSWORD = "PackingDesk2026!";

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
                passwordEncoder.encode(STAFF_PASSWORD),
                "Lars Eriksson",
                Role.STAFF
        ));
    }

    @Test
    @DisplayName("Forbids STAFF from resetting another user's password")
    void shouldForbidStaff() throws Exception {
        MockHttpSession session = login(STAFF_EMAIL, STAFF_PASSWORD);

        mockMvc.perform(post("/api/v1/admin/users/{id}/password-reset", owner.getId())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resetPayload("HarborLantern-2026!")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_PERMISSIONS"));
    }

    @Test
    @DisplayName("Resets the password, flags the account, and records PASSWORD_RESET with the owner as actor")
    void shouldResetPasswordAndFlagAccount() throws Exception {
        MockHttpSession session = login(OWNER_EMAIL, OWNER_PASSWORD);

        mockMvc.perform(post("/api/v1/admin/users/{id}/password-reset", staff.getId())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resetPayload("HarborLantern-2026!")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mustChangePassword").value(true))
                .andExpect(jsonPath("$.data.email").value(STAFF_EMAIL));

        entityManager.flush();
        entityManager.clear();
        User reloaded = userRepository.findById(staff.getId()).orElseThrow();
        assertThat(reloaded.isMustChangePassword()).isTrue();
        assertThat(passwordEncoder.matches("HarborLantern-2026!", reloaded.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches(STAFF_PASSWORD, reloaded.getPasswordHash())).isFalse();

        List<AuthEvent> events = authEventRepository.findAll().stream()
                .filter(event -> event.getEventType() == AuthEventType.PASSWORD_RESET)
                .toList();
        assertThat(events).hasSize(1);
        AuthEvent reset = events.getFirst();
        assertThat(reset.getActorUserId()).isEqualTo(owner.getId());
        assertThat(reset.getActorEmail()).isEqualTo(OWNER_EMAIL);
        assertThat(reset.getSubjectUserId()).isEqualTo(staff.getId());
        assertThat(reset.getSubjectEmail()).isEqualTo(STAFF_EMAIL);
    }

    @Test
    @DisplayName("Blocks an owner from resetting their own password")
    void shouldBlockSelfReset() throws Exception {
        MockHttpSession session = login(OWNER_EMAIL, OWNER_PASSWORD);

        mockMvc.perform(post("/api/v1/admin/users/{id}/password-reset", owner.getId())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resetPayload("HarborLantern-2026!")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SELF_PASSWORD_RESET_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("Returns 404 for an unknown user and WEAK_PASSWORD for a weak replacement")
    void shouldValidateTargetAndPolicy() throws Exception {
        MockHttpSession session = login(OWNER_EMAIL, OWNER_PASSWORD);

        mockMvc.perform(post("/api/v1/admin/users/{id}/password-reset", UUID.randomUUID())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resetPayload("HarborLantern-2026!")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        mockMvc.perform(post("/api/v1/admin/users/{id}/password-reset", staff.getId())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resetPayload("password ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WEAK_PASSWORD"))
                .andExpect(jsonPath("$.invalidParams.length()").value(3))
                .andExpect(jsonPath("$.invalidParams[0].field").value("newPassword"));
    }

    @Test
    @DisplayName("A user created by the owner starts with mustChangePassword=true")
    void shouldFlagNewlyCreatedUsers() throws Exception {
        MockHttpSession session = login(OWNER_EMAIL, OWNER_PASSWORD);

        mockMvc.perform(post("/api/v1/admin/users")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "astrid.lindqvist@nordicliving.se",
                                    "fullName": "Astrid Lindqvist",
                                    "password": "PackingDesk2026!",
                                    "role": "STAFF"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.mustChangePassword").value(true));

        entityManager.flush();
        entityManager.clear();
        User created = userRepository.findByEmailIgnoreCase("astrid.lindqvist@nordicliving.se").orElseThrow();
        assertThat(created.isMustChangePassword()).isTrue();
        assertThat(created.getPasswordChangedAt()).isNotNull();
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

    private String resetPayload(String newPassword) {
        return """
                {
                    "newPassword": "%s"
                }
                """.formatted(newPassword);
    }
}
