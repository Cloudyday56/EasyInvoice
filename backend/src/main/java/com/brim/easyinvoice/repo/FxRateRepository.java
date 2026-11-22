package com.brim.easyinvoice.repo;

import com.brim.easyinvoice.model.FxRate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

public interface FxRateRepository extends JpaRepository<FxRate, LocalDate> {

    Optional<FxRate> findFirstByRateDateLessThanEqualOrderByRateDateDesc(LocalDate date);

    /** USD->CAD rate for the date, or the closest earlier date (weekends have no rate). */
    default Optional<BigDecimal> usdToCadOn(LocalDate date) {
        return findFirstByRateDateLessThanEqualOrderByRateDateDesc(date).map(FxRate::getUsdToCad);
    }
}
