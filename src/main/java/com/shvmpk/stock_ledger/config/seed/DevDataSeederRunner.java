package com.shvmpk.stock_ledger.config.seed;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Executes high-fidelity seed data initialization automatically
 * when running under the 'dev' Spring profile.
 */
@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DevDataSeederRunner implements ApplicationRunner {

    private final DevDataSeeder devDataSeeder;

    @Override
    public void run(ApplicationArguments args) {
        log.info("StockLedger Dev Profile active: populating high-fidelity catalog, inventory, and order seed data...");
        devDataSeeder.seed();
        log.info("StockLedger Dev Profile: seed data population complete.");
    }
}
