package com.brim.easyinvoice.repo;

import com.brim.easyinvoice.model.Role;
import com.brim.easyinvoice.model.User;
import com.brim.easyinvoice.web.NotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class UserRepository {

    private static final RowMapper<User> MAPPER = (rs, i) -> new User(
            rs.getString("id"),
            rs.getString("full_name"),
            rs.getString("email"),
            rs.getString("title"),
            rs.getString("department_id"),
            rs.getString("manager_id"),
            Role.valueOf(rs.getString("role")),
            rs.getString("source_role"),
            rs.getBoolean("read_only"),
            rs.getBigDecimal("approval_limit_cad"),
            rs.getBigDecimal("release_limit_cad"),
            rs.getString("status"),
            rs.getString("delegate_user_id"));

    private final JdbcTemplate jdbc;

    public UserRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<User> findAll() {
        return jdbc.query("SELECT * FROM users ORDER BY id", MAPPER);
    }

    public Optional<User> findById(String id) {
        return jdbc.query("SELECT * FROM users WHERE id = ?", MAPPER, id).stream().findFirst();
    }

    /** Always read fresh from the DB, so limits and status are never stale. */
    public User get(String id) {
        return findById(id).orElseThrow(() -> new NotFoundException("User " + id + " not found"));
    }

    public List<User> findActiveAdmins() {
        return jdbc.query("SELECT * FROM users WHERE role = 'ADMIN' AND status = 'active' AND NOT read_only", MAPPER);
    }

    public Optional<String> departmentManagerId(String departmentId) {
        return jdbc.queryForList("SELECT manager_user_id FROM departments WHERE id = ?", String.class, departmentId)
                .stream().findFirst();
    }
}
