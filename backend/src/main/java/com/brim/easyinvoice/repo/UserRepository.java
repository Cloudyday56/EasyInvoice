package com.brim.easyinvoice.repo;

import com.brim.easyinvoice.model.Role;
import com.brim.easyinvoice.model.User;
import com.brim.easyinvoice.web.NotFoundException;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserRepository extends JpaRepository<User, String> {

    List<User> findAllByOrderById();

    /** Admins who can act (active, not the read-only auditor). */
    List<User> findByRoleAndStatusAndReadOnlyFalse(Role role, String status);

    /** Always read fresh from the DB, so limits and status are never stale. */
    default User get(String id) {
        return findById(id).orElseThrow(() -> new NotFoundException("User " + id + " not found"));
    }
}
