package com.brim.easyinvoice.web;

import com.brim.easyinvoice.model.Notification;
import com.brim.easyinvoice.model.User;
import com.brim.easyinvoice.notify.NotificationService;
import com.brim.easyinvoice.repo.DepartmentRepository;
import com.brim.easyinvoice.repo.UserRepository;
import com.brim.easyinvoice.repo.VendorRepository;
import org.springframework.data.domain.Sort;
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

    /** What the UI needs to show a vendor's name; bank details and tax numbers stay on the server. */
    public record VendorOption(String id, String legalName, String category, String status) {
    }

    private final UserRepository users;
    private final CurrentUser currentUser;
    private final NotificationService notifications;
    private final VendorRepository vendors;
    private final DepartmentRepository departments;

    public UserController(UserRepository users, CurrentUser currentUser, NotificationService notifications,
                          VendorRepository vendors, DepartmentRepository departments) {
        this.users = users;
        this.currentUser = currentUser;
        this.notifications = notifications;
        this.vendors = vendors;
        this.departments = departments;
    }

    /** Public on purpose: the user switcher needs the list before anyone is "logged in". */
    @GetMapping("/users")
    public List<User> allUsers() {
        return users.findAllByOrderById();
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
                "vendors", vendors.findAll(Sort.by("legalName")).stream()
                        .map(v -> new VendorOption(v.getId(), v.getLegalName(), v.getCategory(), v.getStatus())).toList(),
                "departments", departments.findAll(Sort.by("id")));
    }

    @GetMapping("/notifications")
    public List<Notification> myNotifications(@RequestHeader(value = CurrentUser.HEADER, required = false) String userId) {
        return notifications.forUser(currentUser.require(userId).getId());
    }

    @PostMapping("/notifications/read")
    public void markRead(@RequestHeader(value = CurrentUser.HEADER, required = false) String userId) {
        notifications.markAllRead(currentUser.require(userId).getId());
    }
}
