package com.brim.easyinvoice.web;

import com.brim.easyinvoice.model.User;
import com.brim.easyinvoice.notify.Notification;
import com.brim.easyinvoice.notify.NotificationService;
import com.brim.easyinvoice.repo.UserRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class UserController {

    private final UserRepository users;
    private final CurrentUser currentUser;
    private final NotificationService notifications;
    private final JdbcTemplate jdbc;

    public UserController(UserRepository users, CurrentUser currentUser, NotificationService notifications,
                          JdbcTemplate jdbc) {
        this.users = users;
        this.currentUser = currentUser;
        this.notifications = notifications;
        this.jdbc = jdbc;
    }

    /** Public on purpose: the user switcher needs the list before anyone is "logged in". */
    @GetMapping("/users")
    public List<User> allUsers() {
        return users.findAll();
    }

    @GetMapping("/me")
    public User me(@RequestHeader(value = CurrentUser.HEADER, required = false) String userId) {
        return currentUser.require(userId);
    }

    /** Lookup data for the UI (names for ids). */
    @GetMapping("/lookups")
    public Map<String, Object> lookups(@RequestHeader(value = CurrentUser.HEADER, required = false) String userId) {
        currentUser.require(userId);
        return Map.of(
                "vendors", jdbc.queryForList("SELECT id, legal_name, category, status FROM vendors ORDER BY legal_name"),
                "departments", jdbc.queryForList("SELECT id, name, manager_user_id FROM departments ORDER BY id"));
    }

    @GetMapping("/notifications")
    public List<Notification> myNotifications(@RequestHeader(value = CurrentUser.HEADER, required = false) String userId) {
        return notifications.forUser(currentUser.require(userId).id());
    }

    @PostMapping("/notifications/read")
    public void markRead(@RequestHeader(value = CurrentUser.HEADER, required = false) String userId) {
        notifications.markAllRead(currentUser.require(userId).id());
    }
}
