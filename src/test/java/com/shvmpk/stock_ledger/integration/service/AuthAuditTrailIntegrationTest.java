package com.shvmpk.stock_ledger.integration.service;

import com.shvmpk.stock_ledger.TestcontainersConfiguration;
import com.shvmpk.stock_ledger.user.entity.AuthEvent;
import com.shvmpk.stock_ledger.user.entity.AuthEventType;
import com.shvmpk.stock_ledger.user.entity.AuthFailureReason;
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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
@DisplayName("Authentication Audit Trail Integration Tests")
class AuthAuditTrailIntegrationTest {

    private static final String REMOTE_ADDRESS = "198.51.100.24";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 Chrome/141.0 Safari/537.36";
    private static final String REQUEST_ID = "req_0123456789abcdef";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthEventRepository authEventRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private MeterRegistry meterRegistry;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Successful login writes LOGIN_SUCCEEDED with actor, subject, and request metadata")
    void shouldAuditSuccessfulLogin() throws Exception {
        User owner = saveUser("ingrid.bergstrom@nordicliving.se", "OwnerPass2026!", "Ingrid Bergström", Role.OWNER);

        login("ingrid.bergstrom@nordicliving.se", "OwnerPass2026!").andExpect(status().isOk());

        AuthEvent event = singleEvent(AuthEventType.LOGIN_SUCCEEDED);
        assertThat(event.getActorUserId()).isEqualTo(owner.getId());
        assertThat(event.getActorEmail()).isEqualTo("ingrid.bergstrom@nordicliving.se");
        assertThat(event.getSubjectUserId()).isEqualTo(owner.getId());
        assertThat(event.getIpAddress().getHostAddress()).isEqualTo(REMOTE_ADDRESS);
        assertThat(event.getUserAgent()).contains("Chrome/141.0");
        assertThat(event.getRequestId()).isEqualTo(REQUEST_ID);
        assertThat(event.getFailureReason()).isNull();
    }

    @Test
    @DisplayName("A wrong password writes LOGIN_FAILED with INVALID_PASSWORD and no actor id")
    void shouldAuditInvalidPassword() throws Exception {
        User staff = saveUser("lars.eriksson@nordicliving.se", "StaffPass2026!", "Lars Eriksson", Role.STAFF);

        login("lars.eriksson@nordicliving.se", "WrongPassword!").andExpect(status().isUnauthorized());

        AuthEvent event = singleEvent(AuthEventType.LOGIN_FAILED);
        assertThat(event.getFailureReason()).isEqualTo(AuthFailureReason.INVALID_PASSWORD);
        assertThat(event.getActorUserId()).isNull();
        assertThat(event.getActorEmail()).isEqualTo("lars.eriksson@nordicliving.se");
        assertThat(event.getSubjectUserId()).isEqualTo(staff.getId());
    }

    @Test
    @DisplayName("An unknown email writes LOGIN_FAILED with UNKNOWN_EMAIL and no subject")
    void shouldAuditUnknownEmail() throws Exception {
        login("unknown.person@nordicliving.se", "SomePassword2026!").andExpect(status().isUnauthorized());

        AuthEvent event = singleEvent(AuthEventType.LOGIN_FAILED);
        assertThat(event.getFailureReason()).isEqualTo(AuthFailureReason.UNKNOWN_EMAIL);
        assertThat(event.getActorEmail()).isEqualTo("unknown.person@nordicliving.se");
        assertThat(event.getSubjectUserId()).isNull();
        assertThat(event.getSubjectEmail()).isNull();
    }

    @Test
    @DisplayName("An inactive account writes LOGIN_FAILED with INACTIVE_ACCOUNT")
    void shouldAuditInactiveAccount() throws Exception {
        User staff = saveUser("lars.eriksson@nordicliving.se", "StaffPass2026!", "Lars Eriksson", Role.STAFF);
        staff.deactivate();
        userRepository.saveAndFlush(staff);

        login("lars.eriksson@nordicliving.se", "StaffPass2026!").andExpect(status().isUnauthorized());

        AuthEvent event = singleEvent(AuthEventType.LOGIN_FAILED);
        assertThat(event.getFailureReason()).isEqualTo(AuthFailureReason.INACTIVE_ACCOUNT);
        assertThat(event.getSubjectUserId()).isEqualTo(staff.getId());
    }

    @Test
    @DisplayName("A real login increments the success metric through the Spring context")
    void shouldRecordLoginMetric() throws Exception {
        saveUser("ingrid.bergstrom@nordicliving.se", "OwnerPass2026!", "Ingrid Bergström", Role.OWNER);
        double before = meterRegistry.get("stockledger.auth.login")
                .tag("outcome", "success")
                .counter()
                .count();

        login("ingrid.bergstrom@nordicliving.se", "OwnerPass2026!").andExpect(status().isOk());

        double after = meterRegistry.get("stockledger.auth.login")
                .tag("outcome", "success")
                .counter()
                .count();
        assertThat(after).isEqualTo(before + 1);
    }

    @Test
    @DisplayName("Logout writes LOGOUT for the authenticated user")
    void shouldAuditLogout() throws Exception {
        saveUser("ingrid.bergstrom@nordicliving.se", "OwnerPass2026!", "Ingrid Bergström", Role.OWNER);

        MockHttpSession session = (MockHttpSession) login("ingrid.bergstrom@nordicliving.se", "OwnerPass2026!")
                .andExpect(status().isOk())
                .andReturn()
                .getRequest()
                .getSession(false);

        mockMvc.perform(post("/api/v1/auth/logout").session(session))
                .andExpect(status().isOk());

        List<AuthEvent> events = authEventRepository.findAll().stream()
                .filter(event -> event.getEventType() == AuthEventType.LOGOUT)
                .toList();
        assertThat(events).hasSize(1);
        assertThat(events.getFirst().getActorEmail()).isEqualTo("ingrid.bergstrom@nordicliving.se");
    }

    private org.springframework.test.web.servlet.ResultActions login(String email, String password) throws Exception {
        String payload = """
                {
                    "email": "%s",
                    "password": "%s"
                }
                """.formatted(email, password);

        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .header("User-Agent", USER_AGENT)
                .header("X-Request-Id", REQUEST_ID)
                .with(request -> {
                    request.setRemoteAddr(REMOTE_ADDRESS);
                    return request;
                }));
    }

    private AuthEvent singleEvent(AuthEventType type) {
        List<AuthEvent> events = authEventRepository.findAll().stream()
                .filter(event -> event.getEventType() == type)
                .toList();
        assertThat(events).as("Expected exactly one %s event", type).hasSize(1);
        return events.getFirst();
    }

    private User saveUser(String email, String password, String fullName, Role role) {
        return userRepository.saveAndFlush(User.create(
                UUID.randomUUID(),
                email,
                passwordEncoder.encode(password),
                fullName,
                role
        ));
    }
}
