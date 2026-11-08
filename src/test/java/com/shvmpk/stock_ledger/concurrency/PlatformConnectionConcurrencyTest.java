package com.shvmpk.stock_ledger.concurrency;

import com.shvmpk.stock_ledger.TestcontainersConfiguration;
import com.shvmpk.stock_ledger.common.error.ConcurrentChangeException;
import com.shvmpk.stock_ledger.platform.dto.CreatePlatformConnectionRequest;
import com.shvmpk.stock_ledger.platform.dto.PlatformConnectionResponse;
import com.shvmpk.stock_ledger.platform.dto.UpdatePlatformConnectionRequest;
import com.shvmpk.stock_ledger.platform.entity.PlatformType;
import com.shvmpk.stock_ledger.platform.repository.PlatformConnectionEventRepository;
import com.shvmpk.stock_ledger.platform.repository.PlatformConnectionRepository;
import com.shvmpk.stock_ledger.platform.service.PlatformConnectionCreationResult;
import com.shvmpk.stock_ledger.platform.service.PlatformConnectionService;
import com.shvmpk.stock_ledger.support.PlatformConnectionEventTestSupport;
import com.shvmpk.stock_ledger.user.entity.Role;
import com.shvmpk.stock_ledger.user.entity.User;
import com.shvmpk.stock_ledger.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@DisplayName("Platform Connection Concurrency Tests (Duplicate Submit & Simultaneous Edit)")
class PlatformConnectionConcurrencyTest {

    private static final String SHOPIFY_ENDPOINT = "https://northwind-supply.myshopify.com";
    private static final String SHOPIFY_WEBHOOK_SECRET = "dummy-webhook-secret";
    private static final String SHOPIFY_ACCESS_TOKEN = "dummy-shopify-access-token";
    private static final String OWNER_EMAIL = "elin.sandberg@northwind-supply.co";

    @Autowired
    private PlatformConnectionService service;

    @Autowired
    private PlatformConnectionRepository connectionRepository;

    @Autowired
    private PlatformConnectionEventRepository eventRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID ownerId;
    private boolean ownerCreated;

    @BeforeEach
    void setUp() {
        PlatformConnectionEventTestSupport.clear(jdbcTemplate);
        connectionRepository.deleteAllInBatch();
        var existingOwner = userRepository.findByEmailIgnoreCase(OWNER_EMAIL);
        ownerCreated = existingOwner.isEmpty();
        ownerId = existingOwner
                .orElseGet(() -> userRepository.save(User.create(
                        UUID.randomUUID(),
                        OWNER_EMAIL,
                        passwordEncoder.encode("Northwind-Supply-Owner-2026!"),
                        "Elin Sandberg",
                        Role.OWNER
                )))
                .getId();
    }

    @AfterEach
    void tearDown() {
        // These workers commit in their own transactions, so the rows outlive
        // the test unless we remove them; the Flyway tests count committed rows.
        PlatformConnectionEventTestSupport.clear(jdbcTemplate);
        connectionRepository.deleteAllInBatch();
        if (ownerCreated) {
            userRepository.deleteById(ownerId);
        }
    }

    @Test
    @DisplayName("Should create exactly one connection when four workers submit the same request at once")
    void shouldCreateExactlyOneConnectionForConcurrentDuplicateSubmits() throws Exception {
        String requestKey = "a7c2e9f4-3b1d-4e8a-9f6c-2d5b8e1a4c70";
        CreatePlatformConnectionRequest request = new CreatePlatformConnectionRequest(
                PlatformType.SHOPIFY,
                "Northwind Supply",
                null,
                SHOPIFY_ENDPOINT,
                SHOPIFY_WEBHOOK_SECRET,
                SHOPIFY_ACCESS_TOKEN,
                null,
                null
        );

        List<PlatformConnectionCreationResult> results = runConcurrently(
                4,
                () -> service.create(request, ownerId, requestKey)
        );

        UUID connectionId = results.get(0).connection().id();
        assertThat(results).hasSize(4);
        assertThat(results).allSatisfy(result ->
                assertThat(result.connection().id()).isEqualTo(connectionId));
        assertThat(results).filteredOn(PlatformConnectionCreationResult::replayed).hasSize(3);
        assertThat(connectionRepository.count()).isEqualTo(1);
        assertThat(eventRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should let exactly one of four simultaneous edits win and refuse the rest")
    void shouldLetExactlyOneSimultaneousEditWin() throws Exception {
        PlatformConnectionResponse created = service.create(
                new CreatePlatformConnectionRequest(
                        PlatformType.SHOPIFY,
                        "Northwind Supply",
                        null,
                        SHOPIFY_ENDPOINT,
                        SHOPIFY_WEBHOOK_SECRET,
                        SHOPIFY_ACCESS_TOKEN,
                        null,
                        null
                ),
                ownerId,
                null
        ).connection();

        List<String> names = List.of(
                "Northwind Supply EU",
                "Northwind Supply US",
                "Northwind Supply Archive",
                "Northwind Supply Wholesale"
        );

        RaceOutcome outcome = raceEdits(created.id(), created.version(), names);

        assertThat(outcome.winners()).hasSize(1);
        assertThat(outcome.failures())
                .hasSize(3)
                .allSatisfy(failure -> assertThat(failure)
                        .isInstanceOf(ConcurrentChangeException.class));

        PlatformConnectionResponse winner = outcome.winners().get(0);
        PlatformConnectionResponse current = service.get(created.id());
        assertThat(current.displayName()).isEqualTo(winner.displayName());
        assertThat(current.version()).isEqualTo(1);
        assertThat(eventRepository.count()).isEqualTo(2);
    }

    private RaceOutcome raceEdits(UUID connectionId, long version, List<String> names) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(names.size());
        CountDownLatch ready = new CountDownLatch(names.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<PlatformConnectionResponse>> futures = new ArrayList<>();
            for (String name : names) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("The edit race did not start in time.");
                    }
                    return service.update(
                            connectionId,
                            new UpdatePlatformConnectionRequest(
                                    version, name, null, null, null, null, null, null
                            ),
                            ownerId
                    );
                }));
            }

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<PlatformConnectionResponse> winners = new ArrayList<>();
            List<Throwable> failures = new ArrayList<>();
            for (Future<PlatformConnectionResponse> future : futures) {
                try {
                    winners.add(future.get(30, TimeUnit.SECONDS));
                } catch (ExecutionException ex) {
                    failures.add(ex.getCause());
                }
            }
            return new RaceOutcome(winners, failures);
        } finally {
            executor.shutdownNow();
        }
    }

    private <T> List<T> runConcurrently(int workers, Callable<T> task) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (int worker = 0; worker < workers; worker++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("The create race did not start in time.");
                    }
                    return task.call();
                }));
            }

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    private record RaceOutcome(
            List<PlatformConnectionResponse> winners,
            List<Throwable> failures
    ) {
    }
}
