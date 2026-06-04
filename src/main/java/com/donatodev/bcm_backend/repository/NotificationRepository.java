package com.donatodev.bcm_backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.donatodev.bcm_backend.entity.Notification;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByUserIdAndOrgIdOrderByCreatedAtDesc(Long userId, Long orgId);

    long countByUserIdAndOrgIdAndReadFalse(Long userId, Long orgId);
}
