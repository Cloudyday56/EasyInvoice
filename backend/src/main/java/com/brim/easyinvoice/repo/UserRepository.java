package com.brim.easyinvoice.repo;

import org.springframework.data.jpa.repository.EntityGraph;
import java.util.Optional;
import com.brim.easyinvoice.model.UserStatus;
import com.brim.easyinvoice.model.Role;
import com.brim.easyinvoice.model.User;
import com.brim.easyinvoice.web.NotFoundException;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserRepository extends JpaRepository<User, String> {

    /** The user together with the rows authorization reads (department, manager, delegate), usable outside a transaction. */
    @Override
    @EntityGraph(attributePaths = {"department", "manager", "delegate"})
    Optional<User> findById(String id);

    List<User> findAllByOrderById();

    /** Admins who can act (active, not the read-only auditor). */
    List<User> findByRoleAndStatusAndReadOnlyFalse(Role role, UserStatus status);

    /** Always read fresh from the DB, so limits and status are never stale. */
    default User get(String id) {
        return findById(id).orElseThrow(() -> new NotFoundException("User " + id + " not found"));
    }
}
