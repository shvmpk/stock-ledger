package com.shvmpk.stock_ledger.integration.web;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.shvmpk.stock_ledger.TestcontainersConfiguration;
import com.shvmpk.stock_ledger.support.AuthEventTestSupport;
import com.shvmpk.stock_ledger.user.entity.Role;
import com.shvmpk.stock_ledger.user.entity.User;
import com.shvmpk.stock_ledger.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
@DisplayName("Security Log Hygiene Tests")
class SecurityLogHygieneTest {

    private static final String OWNER_EMAIL = "ingrid.bergstrom@nordicliving.se";
    private static final String STAFF_EMAIL = "lars.eriksson@nordicliving.se";
    private static final String OWNER_PASSWORD = "FjordLantern-2026!";
    private static final String STAFF_PASSWORD = "PackingDesk2026!";
    private static final String NEW_PASSWORD = "HarborLantern-2026!";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;
    private ListAppender<ILoggingEvent> auditAppender;
    private Logger auditLogger;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        AuthEventTestSupport.clear(jdbcTemplate);
        userRepository.deleteAll();

        userRepository.saveAndFlush(User.create(
                UUID.randomUUID(),
                OWNER_EMAIL,
                passwordEncoder.encode(OWNER_PASSWORD),
                "Ingrid Bergström",
                Role.OWNER
        ));
        userRepository.saveAndFlush(User.create(
                UUID.randomUUID(),
                STAFF_EMAIL,
                passwordEncoder.encode(STAFF_PASSWORD),
                "Lars Eriksson",
                Role.STAFF
        ));

        auditLogger = (Logger) LoggerFactory.getLogger("SECURITY_AUDIT");
        auditAppender = new ListAppender<>();
        auditAppender.start();
        auditLogger.addAppender(auditAppender);
    }

    @AfterEach
    void tearDown() {
        if (auditLogger != null && auditAppender != null) {
            auditLogger.detachAppender(auditAppender);
            auditAppender.stop();
        }
    }

    @Test
    @DisplayName("Never logs passwords, hashes, session ids, or cookie names")
    void shouldNotLeakCredentialsOrSessionIdentifiers() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Request-Id", "req_login_succeeded01")
                        .content(loginPayload(OWNER_EMAIL, OWNER_PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Request-Id", "req_login_failed01")
                        .content(loginPayload(STAFF_EMAIL, "WrongPassword2026!")))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/password")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Request-Id", "req_password_changed01")
                        .content("""
                                {
                                    "currentPassword": "%s",
                                    "newPassword": "%s"
                                }
                                """.formatted(OWNER_PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/logout")
                        .session(session)
                        .header("X-Request-Id", "req_logout01"))
                .andExpect(status().isOk());

        List<String> lines = auditAppender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .toList();

        assertThat(lines).isNotEmpty();
        for (String forbidden : List.of(OWNER_PASSWORD, STAFF_PASSWORD, NEW_PASSWORD, "$2a$", "$2b$", "JSESSIONID")) {
            assertThat(lines)
                    .as("No security log line may contain '%s'", forbidden)
                    .noneMatch(line -> line.contains(forbidden));
        }
        String sessionId = session.getId();
        if (sessionId.length() >= 8) {
            assertThat(lines).noneMatch(line -> line.contains(sessionId));
        }

        assertThat(lines).allSatisfy(line -> {
            assertThat(line).contains("event=");
            assertThat(line).contains("requestId=");
        });
        assertThat(lines).anySatisfy(line -> assertThat(line)
                .contains("event=LOGIN_SUCCEEDED")
                .contains("requestId=req_login_succeeded01"));
        assertThat(lines).anySatisfy(line -> assertThat(line)
                .contains("event=LOGIN_FAILED")
                .contains("requestId=req_login_failed01"));
        assertThat(lines).anySatisfy(line -> assertThat(line)
                .contains("event=PASSWORD_CHANGED")
                .contains("requestId=req_password_changed01"));
        assertThat(lines).anySatisfy(line -> assertThat(line)
                .contains("event=LOGOUT")
                .contains("requestId=req_logout01"));
    }

    private String loginPayload(String email, String password) {
        return """
                {
                    "email": "%s",
                    "password": "%s"
                }
                """.formatted(email, password);
    }
}
