package com.shvmpk.stock_ledger.integration.repository;

import com.shvmpk.stock_ledger.TestcontainersConfiguration;
import com.shvmpk.stock_ledger.user.entity.Role;
import com.shvmpk.stock_ledger.user.entity.User;
import com.shvmpk.stock_ledger.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
@DisplayName("UserRepository JPA Integration Tests")
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("Should persist and find user by email with case-insensitivity")
    void shouldPersistAndFindByEmail() {
        User user = User.create(
                UUID.randomUUID(),
                "astrid.lindgren@scandiboutique.se",
                "$2a$12$securepasswordhash...",
                "Astrid Lindgren",
                Role.OWNER
        );

        userRepository.save(user);

        Optional<User> found = userRepository.findByEmailIgnoreCase("ASTRID.LINDGREN@SCANDIBOUTIQUE.SE");
        assertThat(found).isPresent();
        assertThat(found.get().getFullName()).isEqualTo("Astrid Lindgren");
        assertThat(found.get().getRole()).isEqualTo(Role.OWNER);
        assertThat(found.get().isActive()).isTrue();
    }

    @Test
    @DisplayName("Should verify existsByEmailIgnoreCase correctly")
    void shouldCheckEmailExistence() {
        User user = User.create(
                UUID.randomUUID(),
                "erik.thor@scandiboutique.se",
                "hash",
                "Erik Thor",
                Role.STAFF
        );
        userRepository.save(user);

        assertThat(userRepository.existsByEmailIgnoreCase("erik.thor@scandiboutique.se")).isTrue();
        assertThat(userRepository.existsByEmailIgnoreCase("unknown@scandiboutique.se")).isFalse();
    }

    @Test
    @DisplayName("Should find all users by role")
    void shouldFindAllByRole() {
        userRepository.save(User.create(UUID.randomUUID(), "staff1@test.com", "h", "Staff One", Role.STAFF));
        userRepository.save(User.create(UUID.randomUUID(), "staff2@test.com", "h", "Staff Two", Role.STAFF));
        userRepository.save(User.create(UUID.randomUUID(), "owner1@test.com", "h", "Owner One", Role.OWNER));

        List<User> staffMembers = userRepository.findAllByRole(Role.STAFF);
        assertThat(staffMembers).hasSize(2);
    }
}
