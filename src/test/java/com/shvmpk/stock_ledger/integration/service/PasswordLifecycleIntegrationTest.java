package com.shvmpk.stock_ledger.integration.service;

import com.shvmpk.stock_ledger.TestcontainersConfiguration;
import com.shvmpk.stock_ledger.support.AuthEventTestSupport;
import com.shvmpk.stock_ledger.user.entity.AuthEvent;
import com.shvmpk.stock_ledger.user.entity.AuthEventType;
import com.shvmpk.stock_ledger.user.entity.Role;
import com.shvmpk.stock_ledger.user.entity.User;
import com.shvmpk.stock_ledger.user.repository.AuthEventRepository;
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

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
@DisplayName("Password Lifecycle Journey (Owner Reset to Staff Recovery)")
class PasswordLifecycleIntegrationTest {

    private static final String OWNER_EMAIL = "ingrid.bergstrom@nordicliving.se";
    private static final String STAFF_EMAIL = "lars.eriksson@nordicliving.se";
    private static final String OWNER_PASSWORD = "FjordLantern-2026!";
    private static final String TEMPORARY_PASSWORD = "TemporaryPass2026!";
    private static final String FINAL_PASSWORD = "HarborLantern-2026!";

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
    @DisplayName("Owner reset, staff forced change, restored access, and an ordered RESET -> CHANGED audit trail")
    void shouldCompleteTheForcedChangeJourney() throws Exception {
        MockHttpSession ownerSession = login(OWNER_EMAIL, OWNER_PASSWORD);

        mockMvc.perform(post("/api/v1/admin/users/{id}/password-reset", staff.getId())
                        .session(ownerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resetPayload(TEMPORARY_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mustChangePassword").value(true));

        MockHttpSession staffSession = login(STAFF_EMAIL, TEMPORARY_PASSWORD);

        mockMvc.perform(get("/api/v1/stock/movements").session(staffSession))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));

        mockMvc.perform(post("/api/v1/auth/password")
                        .session(staffSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "newPassword": "password "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WEAK_PASSWORD"));

        mockMvc.perform(post("/api/v1/auth/password")
                        .session(staffSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "newPassword": "%s"
                                }
                                """.formatted(FINAL_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mustChangePassword").value(false));

        mockMvc.perform(get("/api/v1/stock/movements").session(staffSession))
                .andExpect(status().isOk());

        List<AuthEvent> lifecycle = authEventRepository.findAll().stream()
                .filter(event -> event.getEventType() == AuthEventType.PASSWORD_RESET
                        || event.getEventType() == AuthEventType.PASSWORD_CHANGED)
                .sorted((left, right) -> left.getOccurredAt().compareTo(right.getOccurredAt()))
                .toList();

        assertThat(lifecycle).extracting(AuthEvent::getEventType)
                .containsExactly(AuthEventType.PASSWORD_RESET, AuthEventType.PASSWORD_CHANGED);
        assertThat(lifecycle.get(0).getActorUserId()).isEqualTo(owner.getId());
        assertThat(lifecycle.get(0).getSubjectUserId()).isEqualTo(staff.getId());
        assertThat(lifecycle.get(1).getActorUserId()).isEqualTo(staff.getId());
        assertThat(lifecycle.get(1).getSubjectUserId()).isEqualTo(staff.getId());
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
