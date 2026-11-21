package com.brim.easyinvoice.db;

import com.brim.easyinvoice.repo.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * On startup: load /data into the database if it is empty.
 * (The tables themselves are created earlier by Hibernate, from the entities.)
 */
@Component
public class StartupSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupSeeder.class);

    private final UserRepository users;
    private final SeedLoader seedLoader;

    public StartupSeeder(UserRepository users, SeedLoader seedLoader) {
        this.users = users;
        this.seedLoader = seedLoader;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (users.count() == 0) {
            seedLoader.seedAll();
        } else {
            log.info("Database already has data; skipping seed (use Reset demo to reseed)");
        }
    }
}
