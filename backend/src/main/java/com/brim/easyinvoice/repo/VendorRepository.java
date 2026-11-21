package com.brim.easyinvoice.repo;

import com.brim.easyinvoice.model.Vendor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class VendorRepository {

    private static final RowMapper<Vendor> MAPPER = (rs, i) -> new Vendor(
            rs.getString("id"),
            rs.getString("legal_name"),
            rs.getString("category"),
            rs.getString("default_gl_account"),
            rs.getString("status"),
            rs.getBoolean("accepts_card_payment"),
            (Integer) rs.getObject("payment_terms_days"),
            rs.getString("ap_contact_email"));

    private final JdbcTemplate jdbc;

    public VendorRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Vendor> findById(String id) {
        if (id == null) return Optional.empty();
        return jdbc.query("SELECT * FROM vendors WHERE id = ?", MAPPER, id).stream().findFirst();
    }

    public Optional<Vendor> findByLegalNameIgnoreCase(String name) {
        return jdbc.query("SELECT * FROM vendors WHERE lower(legal_name) = lower(?)", MAPPER, name)
                .stream().findFirst();
    }

    public List<Vendor> findAllActive() {
        return jdbc.query("SELECT * FROM vendors WHERE status = 'active' ORDER BY id", MAPPER);
    }
}
