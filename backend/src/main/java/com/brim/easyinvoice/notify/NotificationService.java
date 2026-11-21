package com.brim.easyinvoice.notify;

import com.brim.easyinvoice.model.User;
import com.brim.easyinvoice.repo.UserRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;

/** In-app inbox. Email is simulated: we only write a row the UI can show. */
@Service
public class NotificationService {

    private final JdbcTemplate jdbc;
    private final UserRepository users;

    public NotificationService(JdbcTemplate jdbc, UserRepository users) {
        this.jdbc = jdbc;
        this.users = users;
    }

    public void notify(String userId, String template, String invoiceId, String message) {
        if (userId == null) return;
        jdbc.update("INSERT INTO notifications (user_id, template, invoice_id, message) VALUES (?, ?, ?, ?)",
                userId, template, invoiceId, message);
    }

    public void notifyAdmins(String template, String invoiceId, String message) {
        for (User admin : users.findActiveAdmins()) {
            notify(admin.id(), template, invoiceId, message);
        }
    }

    public List<Notification> forUser(String userId) {
        return jdbc.query("SELECT * FROM notifications WHERE user_id = ? ORDER BY id DESC LIMIT 50",
                (rs, i) -> new Notification(rs.getLong("id"), rs.getString("user_id"), rs.getString("template"),
                        rs.getString("invoice_id"), rs.getString("message"), rs.getBoolean("is_read"),
                        rs.getObject("created_at", OffsetDateTime.class)), userId);
    }

    public void markAllRead(String userId) {
        jdbc.update("UPDATE notifications SET is_read = TRUE WHERE user_id = ?", userId);
    }
}
