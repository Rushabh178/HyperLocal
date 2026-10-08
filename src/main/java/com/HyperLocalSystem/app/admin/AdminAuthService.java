package com.HyperLocalSystem.app.admin;

import com.HyperLocalSystem.app.admin.dto.AdminDtos.AdminProfileResponse;
import com.HyperLocalSystem.app.admin.entity.AdminUser;
import com.HyperLocalSystem.app.admin.repository.AdminUserRepository;
import com.HyperLocalSystem.app.auth.service.AccountAuthService;
import com.HyperLocalSystem.app.auth.service.OtpService;
import com.HyperLocalSystem.app.security.Role;
import com.HyperLocalSystem.app.security.TokenService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * Ops-console sign-in: password login (email or phone), refresh and logout, sharing the flow of the
 * other apps. Tokens carry ROLE_ADMIN. There is no sign-up; see {@link AdminSeeder}.
 */
@Service
public class AdminAuthService extends AccountAuthService<AdminUser, AdminProfileResponse> {

    private final AdminUserRepository admins;

    public AdminAuthService(AdminUserRepository admins, OtpService otpService, TokenService tokenService,
                            PasswordEncoder passwordEncoder) {
        super(otpService, tokenService, passwordEncoder);
        this.admins = admins;
    }

    @Override
    protected Role role() {
        return Role.ADMIN;
    }

    @Override
    protected Optional<AdminUser> findByPhone(String normalizedPhone) {
        return admins.findByPhone(normalizedPhone);
    }

    @Override
    protected Optional<AdminUser> findByEmail(String normalizedEmail) {
        return admins.findByEmailIgnoreCase(normalizedEmail);
    }

    @Override
    protected Optional<AdminUser> findById(UUID id) {
        return admins.findById(id);
    }

    @Override
    protected AdminProfileResponse toProfile(AdminUser admin) {
        return new AdminProfileResponse(admin.getId(), admin.getFullName(), admin.getEmail(), admin.getPhone(), admin.getCreatedAt());
    }
}
