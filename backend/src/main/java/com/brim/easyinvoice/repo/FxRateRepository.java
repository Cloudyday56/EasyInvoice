package com.brim.easyinvoice.repo;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

@Repository
public class FxRateRepository {

    private final JdbcTemplate jdbc;

    public FxRateRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** USD->CAD rate for the date, or the closest earlier date (weekends have no rate). */
    public Optional<BigDecimal> usdToCadOn(LocalDate date) {
        return jdbc.queryForList("SELECT usd_to_cad FROM fx_rates WHERE date <= ? ORDER BY date DESC LIMIT 1",
                BigDecimal.class, date).stream().findFirst();
    }
}
