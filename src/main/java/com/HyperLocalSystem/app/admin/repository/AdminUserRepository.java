package com.HyperLocalSystem.app.admin.repository;

import com.HyperLocalSystem.app.admin.entity.AdminUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AdminUserRepository extends JpaRepository<AdminUser, UUID> {

    Optional<AdminUser> findByEmailIgnoreCase(String email);

    Optional<AdminUser> findByPhone(String phone);
}
