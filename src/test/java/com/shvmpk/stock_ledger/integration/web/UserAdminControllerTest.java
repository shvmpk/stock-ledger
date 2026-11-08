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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
@DisplayName("UserAdminController Integration Tests (Task 2)")
class UserAdminControllerTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private MockMvc mockMvc;
    private User owner;
    private User existingStaff;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        userRepository.deleteAll();

        owner = User.create(UUID.randomUUID(), "owner@stockledger.io", passwordEncoder.encode("Pass12345!"), "Ingrid Bergström", Role.OWNER);
        existingStaff = User.create(UUID.randomUUID(), "staff@stockledger.io", passwordEncoder.encode("Pass12345!"), "Lars Eriksson", Role.STAFF);

        userRepository.save(owner);
        userRepository.save(existingStaff);
    }

    @Test
    @WithMockUser(roles = "STAFF")
    @DisplayName("STAFF accessing /api/v1/admin/users should receive 403 Forbidden")
    void staffShouldBeForbiddenFromUserAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "OWNER")
    @DisplayName("OWNER can list all users")
    void ownerCanListUsers() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    @WithMockUser(roles = "OWNER")
    @DisplayName("OWNER can create a new staff member")
    void ownerCanCreateStaffUser() throws Exception {
        String payload = """
                {
                    "email": "new.packer@stockledger.io",
                    "fullName": "Karin Nygård",
                    "password": "HarborLantern-2026!",
                    "role": "STAFF"
                }
                """;

        mockMvc.perform(post("/api/v1/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("new.packer@stockledger.io"))
                .andExpect(jsonPath("$.data.fullName").value("Karin Nygård"))
                .andExpect(jsonPath("$.data.role").value("STAFF"));

        assertThat(userRepository.existsByEmailIgnoreCase("new.packer@stockledger.io")).isTrue();
    }

    @Test
    @WithMockUser(roles = "OWNER")
    @DisplayName("Creating user with duplicate email returns 409 Conflict")
    void rejectDuplicateEmail() throws Exception {
        String payload = """
                {
                    "email": "staff@stockledger.io",
                    "fullName": "Duplicate Staff",
                    "password": "PackerPass2026!",
                    "role": "STAFF"
                }
                """;

        mockMvc.perform(post("/api/v1/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(roles = "OWNER")
    @DisplayName("OWNER can delete/deactivate user")
    void ownerCanDeactivateUser() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/users/" + existingStaff.getId()))
                .andExpect(status().isOk());

        User reloaded = userRepository.findById(existingStaff.getId()).orElseThrow();
        assertThat(reloaded.isActive()).isFalse();
    }

    @Test
    @WithMockUser(username = "owner@stockledger.io", roles = "OWNER")
    @DisplayName("PATCH /api/v1/admin/users/{id} should change a user's full name")
    void ownerCanUpdateFullName() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/users/" + existingStaff.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Karin Nygård"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.fullName").value("Karin Nygård"))
                .andExpect(jsonPath("$.data.role").value("STAFF"));

        User reloaded = userRepository.findById(existingStaff.getId()).orElseThrow();
        assertThat(reloaded.getFullName()).isEqualTo("Karin Nygård");
        assertThat(reloaded.getRole()).isEqualTo(Role.STAFF);
    }

    @Test
    @WithMockUser(username = "owner@stockledger.io", roles = "OWNER")
    @DisplayName("PATCH /api/v1/admin/users/{id} should change a user's role")
    void ownerCanUpdateRole() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/users/" + existingStaff.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "role": "OWNER"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("OWNER"));

        assertThat(userRepository.findById(existingStaff.getId()).orElseThrow().getRole())
                .isEqualTo(Role.OWNER);
    }

    @Test
    @WithMockUser(username = "owner@stockledger.io", roles = "OWNER")
    @DisplayName("PATCH /api/v1/admin/users/{id} should reactivate an inactive user")
    void ownerCanReactivateUser() throws Exception {
        existingStaff.deactivate();
        userRepository.saveAndFlush(existingStaff);

        mockMvc.perform(patch("/api/v1/admin/users/" + existingStaff.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "active": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(true));

        assertThat(userRepository.findById(existingStaff.getId()).orElseThrow().isActive()).isTrue();
    }

    @Test
    @WithMockUser(roles = "OWNER")
    @DisplayName("PATCH and DELETE should protect the last active owner")
    void shouldProtectLastActiveOwner() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/users/" + owner.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "active": false
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LAST_OWNER_PROTECTED"));

        mockMvc.perform(delete("/api/v1/admin/users/" + owner.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LAST_OWNER_PROTECTED"));

        assertThat(userRepository.findById(owner.getId()).orElseThrow().isActive()).isTrue();
    }

    @Test
    @WithMockUser(roles = "OWNER")
    @DisplayName("PATCH should protect the last active owner from demotion")
    void shouldProtectLastActiveOwnerFromDemotion() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/users/" + owner.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "role": "STAFF"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LAST_OWNER_PROTECTED"));

        assertThat(userRepository.findById(owner.getId()).orElseThrow().getRole()).isEqualTo(Role.OWNER);
    }

    @Test
    @WithMockUser(username = "owner@stockledger.io", roles = "OWNER")
    @DisplayName("An owner cannot deactivate their own account")
    void shouldRejectSelfDeactivation() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/users/" + owner.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "active": false
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CANNOT_DEACTIVATE_SELF"));

        mockMvc.perform(delete("/api/v1/admin/users/" + owner.getId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CANNOT_DEACTIVATE_SELF"));

        assertThat(userRepository.findById(owner.getId()).orElseThrow().isActive()).isTrue();
    }

    @Test
    @WithMockUser(roles = "OWNER")
    @DisplayName("PATCH with no fields should return 400 NO_USER_CHANGES")
    void shouldRejectEmptyPatch() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/users/" + existingStaff.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NO_USER_CHANGES"));
    }

    @Test
    @WithMockUser(roles = "OWNER")
    @DisplayName("PATCH with a blank full name should return 400 VALIDATION_FAILED")
    void shouldRejectBlankFullName() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/users/" + existingStaff.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "   "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.invalidParams[0].field").value("fullName"));
    }

    @Test
    @WithMockUser(roles = "STAFF")
    @DisplayName("STAFF cannot PATCH or DELETE user administration resources")
    void staffCannotUpdateOrDeactivateUsers() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/users/" + existingStaff.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "role": "OWNER"
                                }
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/admin/users/" + existingStaff.getId()))
                .andExpect(status().isForbidden());
    }
}
