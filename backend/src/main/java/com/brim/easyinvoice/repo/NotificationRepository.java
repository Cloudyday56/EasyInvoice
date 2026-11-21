package com.brim.easyinvoice.repo;

import com.brim.easyinvoice.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findTop50ByUserIdOrderByIdDesc(String userId);

    List<Notification> findByUserIdAndReadFalse(String userId);
}
