package com.brim.easyinvoice.notify;

import com.brim.easyinvoice.model.UserStatus;
import com.brim.easyinvoice.model.Invoice;
import com.brim.easyinvoice.model.Notification;
import com.brim.easyinvoice.model.Role;
import com.brim.easyinvoice.model.User;
import com.brim.easyinvoice.repo.NotificationRepository;
import com.brim.easyinvoice.repo.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** In-app inbox. Email is simulated: we only write a row the UI can show. */
@Service
public class NotificationService {

    private final NotificationRepository notifications;
    private final UserRepository users;

    public NotificationService(NotificationRepository notifications, UserRepository users) {
        this.notifications = notifications;
        this.users = users;
    }

    public void notify(User user, String template, Invoice invoice, String message) {
        if (user == null) return;
        Notification n = new Notification();
        n.setUser(user);
        n.setTemplate(template);
        n.setInvoice(invoice);
        n.setMessage(message);
        notifications.save(n);
    }

    public void notifyAdmins(String template, Invoice invoice, String message) {
        for (User admin : users.findByRoleAndStatusAndReadOnlyFalse(Role.ADMIN, UserStatus.ACTIVE)) {
            notify(admin, template, invoice, message);
        }
    }

    public List<Notification> forUser(String userId) {
        return notifications.findTop50ByUserIdOrderByIdDesc(userId);
    }

    @Transactional
    public void markAllRead(String userId) {
        notifications.findByUserIdAndReadFalse(userId).forEach(n -> n.setRead(true));
    }
}
