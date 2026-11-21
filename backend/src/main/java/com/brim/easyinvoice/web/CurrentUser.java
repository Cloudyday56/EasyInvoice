package com.brim.easyinvoice.web;

import com.brim.easyinvoice.model.User;
import com.brim.easyinvoice.repo.UserRepository;
import org.springframework.stereotype.Component;

/**
 * "Login as user" for the demo: the UI's user switcher sends the chosen user id in the
 * X-User-Id header. No passwords. The user row is re-read on every request, so role,
 * status and limits are always current.
 */
@Component
public class CurrentUser {

    public static final String HEADER = "X-User-Id";

    private final UserRepository users;

    public CurrentUser(UserRepository users) {
        this.users = users;
    }

    public User require(String userIdHeader) {
        if (userIdHeader == null || userIdHeader.isBlank()) {
            throw new UnauthorizedException("Pick a user first (missing " + HEADER + " header)");
        }
        return users.findById(userIdHeader)
                .orElseThrow(() -> new UnauthorizedException("Unknown user " + userIdHeader));
    }
}
