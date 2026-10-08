package com.HyperLocalSystem.app.admin;

import com.HyperLocalSystem.app.admin.entity.AdminUser;
import com.HyperLocalSystem.app.admin.repository.AdminUserRepository;
import com.HyperLocalSystem.app.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Creates the first admin from ADMIN_EMAIL / ADMIN_PASSWORD (and optional ADMIN_NAME) on startup,
 * if that email has no admin yet. Never changes an existing admin, so it is safe on every start and
 * on every instance (a race between instances just hits the unique email and is ignored).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminSeeder implements ApplicationRunner {

    private final AdminUserRepository admins;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties properties;

    /** Not transactional on purpose: a lost race on the unique email must not poison a surrounding transaction. */
    @Override
    public void run(ApplicationArguments args) {
        AppProperties.Admin seed = properties.admin();
        if (seed == null || isBlank(seed.seedEmail()) || isBlank(seed.seedPassword())) {
            return;
        }
        String email = seed.seedEmail().trim().toLowerCase(Locale.ROOT);
        if (admins.findByEmailIgnoreCase(email).isPresent()) {
            return;
        }
        if (seed.seedPassword().length() < 8) {
            log.warn("ADMIN_PASSWORD is shorter than 8 characters; admin {} was not created", email);
            return;
        }
        AdminUser admin = new AdminUser();
        admin.setEmail(email);
        admin.setFullName(isBlank(seed.seedName()) ? "Ops Admin" : seed.seedName().trim());
        admin.setPasswordHash(passwordEncoder.encode(seed.seedPassword()));
        try {
            admins.saveAndFlush(admin);
            log.info("Seeded admin {}", email);
        } catch (DataIntegrityViolationException e) {
            log.info("Admin {} was created by another instance", email);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
