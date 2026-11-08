package com.shvmpk.stock_ledger.integration.web;

import com.shvmpk.stock_ledger.TestcontainersConfiguration;
import com.shvmpk.stock_ledger.support.AuthEventTestSupport;
import com.shvmpk.stock_ledger.user.entity.AuthEvent;
import com.shvmpk.stock_ledger.user.entity.AuthEventType;
import com.shvmpk.stock_ledger.user.entity.AuthFailureReason;
import com.shvmpk.stock_ledger.user.repository.AuthEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
@DisplayName("Auth Event Audit Read API Tests (Owner-only Security Log)")
class AuthEventControllerTest {

    private static final String OWNER_EMAIL = "ingrid.bergstrom@nordicliving.se";
    private static final String STAFF_EMAIL = "lars.eriksson@nordicliving.se";
    private static final String OWNER_USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 Chrome/141.0 Safari/537.36";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private AuthEventRepository authEventRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;
    private UUID ownerId;
    private UUID staffId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        AuthEventTestSupport.clear(jdbcTemplate);
        ownerId = UUID.randomUUID();
        staffId = UUID.randomUUID();
    }

    @Test
    @WithMockUser(username = OWNER_EMAIL, roles = "OWNER")
    @DisplayName("Returns the paginated security log newest first with complete pagination metadata")
    void shouldReturnNewestFirstPaginatedEnvelope() throws Exception {
        seedThreeEvents();

        mockMvc.perform(get("/api/v1/admin/auth-events").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].eventType").value("LOGIN_FAILED"))
                .andExpect(jsonPath("$.data[1].eventType").value("LOGIN_SUCCEEDED"))
                .andExpect(jsonPath("$.pagination.page").value(0))
                .andExpect(jsonPath("$.pagination.size").value(2))
                .andExpect(jsonPath("$.pagination.totalElements").value(3))
                .andExpect(jsonPath("$.pagination.totalPages").value(2))
                .andExpect(jsonPath("$.pagination.hasMore").value(true))
                .andExpect(jsonPath("$.meta.timestamp").exists());

        mockMvc.perform(get("/api/v1/admin/auth-events").param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].eventType").value("LOGOUT"))
                .andExpect(jsonPath("$.pagination.page").value(1))
                .andExpect(jsonPath("$.pagination.hasMore").value(false));
    }

    @Test
    @WithMockUser(username = OWNER_EMAIL, roles = "OWNER")
    @DisplayName("Filters by event type, actor email case-insensitively, and an inclusive date range")
    void shouldFilterSecurityEvents() throws Exception {
        seedThreeEvents();

        mockMvc.perform(get("/api/v1/admin/auth-events").param("eventType", "LOGIN_FAILED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].failureReason").value("INVALID_PASSWORD"))
                .andExpect(jsonPath("$.data[0].actorEmail").value(STAFF_EMAIL));

        mockMvc.perform(get("/api/v1/admin/auth-events")
                        .param("actorEmail", "INGRID.BERGSTROM@NORDICLIVING.SE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.pagination.totalElements").value(2));

        mockMvc.perform(get("/api/v1/admin/auth-events")
                        .param("from", "2026-10-26T10:00:00Z")
                        .param("to", "2026-10-26T11:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].eventType").value("LOGIN_FAILED"))
                .andExpect(jsonPath("$.data[1].eventType").value("LOGIN_SUCCEEDED"));
    }

    @Test
    @WithMockUser(username = OWNER_EMAIL, roles = "OWNER")
    @DisplayName("Serializes the IP address as a string and passes role transitions to the viewer")
    void shouldExposeReadableAuditFields() throws Exception {
        authEventRepository.saveAndFlush(AuthEvent.builder()
                .eventType(AuthEventType.ROLE_CHANGED)
                .actorUserId(ownerId)
                .actorEmail(OWNER_EMAIL)
                .subjectUserId(staffId)
                .subjectEmail(STAFF_EMAIL)
                .ipAddress(InetAddress.getByName("203.0.113.42"))
                .userAgent(OWNER_USER_AGENT)
                .requestId("req_0123456789abcdef")
                .details(Map.of("fromRole", "STAFF", "toRole", "OWNER"))
                .occurredAt(at(11, 0))
                .build());

        mockMvc.perform(get("/api/v1/admin/auth-events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].ipAddress").value("203.0.113.42"))
                .andExpect(jsonPath("$.data[0].actorEmail").value(OWNER_EMAIL))
                .andExpect(jsonPath("$.data[0].subjectEmail").value(STAFF_EMAIL))
                .andExpect(jsonPath("$.data[0].details.fromRole").value("STAFF"))
                .andExpect(jsonPath("$.data[0].details.toRole").value("OWNER"))
                .andExpect(jsonPath("$.data[0].occurredAt").exists());
    }

    @Test
    @WithMockUser(username = OWNER_EMAIL, roles = "OWNER")
    @DisplayName("Filters the viewer by several event types in one request")
    void shouldFilterByMultipleEventTypes() throws Exception {
        seedThreeEvents();

        mockMvc.perform(get("/api/v1/admin/auth-events")
                        .param("eventType", "LOGIN_SUCCEEDED", "LOGIN_FAILED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].eventType").value("LOGIN_FAILED"))
                .andExpect(jsonPath("$.data[1].eventType").value("LOGIN_SUCCEEDED"))
                .andExpect(jsonPath("$.pagination.totalElements").value(2));
    }

    @Test
    @WithMockUser(username = OWNER_EMAIL, roles = "OWNER")
    @DisplayName("Clamps an oversized page size to 100")
    void shouldClampOversizedPageSize() throws Exception {
        seedThreeEvents();

        mockMvc.perform(get("/api/v1/admin/auth-events").param("size", "500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.size").value(100));
    }

    @Test
    @WithMockUser(username = OWNER_EMAIL, roles = "OWNER")
    @DisplayName("Rejects a negative page index with VALIDATION_FAILED")
    void shouldRejectNegativePageIndex() throws Exception {
        mockMvc.perform(get("/api/v1/admin/auth-events").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.invalidParams[0].field").value("page"));
    }

    @Test
    @WithMockUser(username = OWNER_EMAIL, roles = "OWNER")
    @DisplayName("Rejects an inverted date range with INVALID_DATE_RANGE")
    void shouldRejectInvertedDateRange() throws Exception {
        mockMvc.perform(get("/api/v1/admin/auth-events")
                        .param("from", "2026-11-01T00:00:00Z")
                        .param("to", "2026-10-01T00:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DATE_RANGE"))
                .andExpect(jsonPath("$.title").value("Invalid Date Range"));
    }

    @Test
    @WithMockUser(username = OWNER_EMAIL, roles = "OWNER")
    @DisplayName("Never serializes password hashes, tokens, or secrets")
    void shouldNeverSerializeCredentialFields() throws Exception {
        authEventRepository.saveAndFlush(AuthEvent.builder()
                .eventType(AuthEventType.PASSWORD_CHANGED)
                .actorUserId(ownerId)
                .actorEmail(OWNER_EMAIL)
                .subjectUserId(ownerId)
                .subjectEmail(OWNER_EMAIL)
                .ipAddress(InetAddress.getByName("198.51.100.24"))
                .userAgent(OWNER_USER_AGENT)
                .details(Map.of("reason", "Routine credential rotation at the warehouse office"))
                .occurredAt(at(12, 0))
                .build());

        String body = mockMvc.perform(get("/api/v1/admin/auth-events"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(body)
                .doesNotContain("passwordHash")
                .doesNotContain("password_hash")
                .doesNotContain("rawPassword")
                .doesNotContain("refreshToken")
                .doesNotContain("webhookSecret")
                .doesNotContain("$2a$");
        assertThat(body).contains("\"actorEmail\"", "\"subjectEmail\"", "\"occurredAt\"");
    }

    @Test
    @WithMockUser(username = STAFF_EMAIL, roles = "STAFF")
    @DisplayName("Forbids STAFF from reading the security log")
    void shouldForbidStaff() throws Exception {
        mockMvc.perform(get("/api/v1/admin/auth-events"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_PERMISSIONS"));
    }

    @Test
    @DisplayName("Requires authentication to read the security log")
    void shouldRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/admin/auth-events"))
                .andExpect(status().isUnauthorized());
    }

    private void seedThreeEvents() throws UnknownHostException {
        authEventRepository.save(AuthEvent.builder()
                .eventType(AuthEventType.LOGOUT)
                .actorUserId(ownerId)
                .actorEmail(OWNER_EMAIL)
                .subjectUserId(ownerId)
                .subjectEmail(OWNER_EMAIL)
                .ipAddress(InetAddress.getByName("203.0.113.42"))
                .userAgent(OWNER_USER_AGENT)
                .occurredAt(at(9, 0))
                .build());

        authEventRepository.save(AuthEvent.builder()
                .eventType(AuthEventType.LOGIN_SUCCEEDED)
                .actorUserId(ownerId)
                .actorEmail(OWNER_EMAIL)
                .subjectUserId(ownerId)
                .subjectEmail(OWNER_EMAIL)
                .ipAddress(InetAddress.getByName("203.0.113.42"))
                .userAgent(OWNER_USER_AGENT)
                .occurredAt(at(10, 0))
                .build());

        authEventRepository.saveAndFlush(AuthEvent.builder()
                .eventType(AuthEventType.LOGIN_FAILED)
                .failureReason(AuthFailureReason.INVALID_PASSWORD)
                .actorEmail(STAFF_EMAIL)
                .subjectUserId(staffId)
                .subjectEmail(STAFF_EMAIL)
                .ipAddress(InetAddress.getByName("198.51.100.24"))
                .userAgent(OWNER_USER_AGENT)
                .occurredAt(at(11, 0))
                .build());
    }

    private OffsetDateTime at(int hour, int minute) {
        return OffsetDateTime.of(2026, 10, 26, hour, minute, 0, 0, ZoneOffset.UTC);
    }
}
