package com.brim.easyinvoice.db;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * On startup: run schema.sql (every statement is IF NOT EXISTS, so this is safe each time),
 * then seed from /data only if the database is empty.
 *
 * Runs in @PostConstruct, i.e. while the app is starting and before the pipeline's
 * scheduled loop begins, so the runner never sees a missing table.
 */
@Component
public class DatabaseInitializer {

    private static final Logger log = LoggerFactory.getLogger(DatabaseInitializer.class);

    private final JdbcTemplate jdbc;
    private final SeedLoader seedLoader;

    public DatabaseInitializer(JdbcTemplate jdbc, SeedLoader seedLoader) {
        this.jdbc = jdbc;
        this.seedLoader = seedLoader;
    }

    @PostConstruct
    public void initialize() throws IOException {
        String schema = new ClassPathResource("schema.sql").getContentAsString(StandardCharsets.UTF_8);
        jdbc.execute(schema); // the Postgres driver runs a multi-statement script in one call
        log.info("Schema ready");

        if (isEmpty()) {
            seedLoader.seedAll();
        } else {
            log.info("Database already has data; skipping seed (use Reset demo to reseed)");
        }
    }

    public boolean isEmpty() {
        Integer users = jdbc.queryForObject("SELECT count(*) FROM users", Integer.class);
        return users == null || users == 0;
    }
}
