package com.donatodev.bcm_backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.donatodev.bcm_backend.entity.Managers;
import com.donatodev.bcm_backend.entity.Organization;
import com.donatodev.bcm_backend.entity.Roles;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.repository.ManagersRepository;
import com.donatodev.bcm_backend.repository.OrganizationRepository;
import com.donatodev.bcm_backend.repository.RolesRepository;
import com.donatodev.bcm_backend.repository.UsersRepository;

/**
 * Seeds one working login on a fresh database, dev only. Without this, a
 * brand-new `docker compose up` has no usable account: V4 creates a default
 * admin, but V14 disables it in the same migration run (its password hash is
 * publicly known from this repo's own history — see V14's comment). That's
 * the correct call for a real deployment, but it means a first-time
 * `docker compose up` currently produces four healthy containers and no way
 * to log in, which defeats the point of a one-command demo.
 * <p>
 * Reuses the organization and reference data V2/V7 already seed (business
 * areas, financial types) rather than creating a parallel demo org — this
 * account just needs a valid login, not a separate dataset. Idempotent: does
 * nothing if the account already exists, so restarts are harmless.
 * <p>
 * Also creates a {@link Managers} row: {@code users.manager_id} is
 * {@code NOT NULL} in the real schema (V1) even though the JPA entity marks
 * the association {@code nullable = true} — that mismatch made the first
 * version of this seeder crash the whole application on startup with a
 * constraint violation, since a failed {@link ApplicationRunner} aborts
 * context refresh entirely. Verified live against a real database, not just
 * inferred from the entity annotation, after that failure.
 */
@Component
@Profile("dev")
public class DevDataSeeder implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(DevDataSeeder.class);

    static final String DEMO_USERNAME = "demo";
    @SuppressWarnings("java:S2068") // dev-profile-only demo credential, intentionally documented in the log line below
    static final String DEMO_PASSWORD = "Demo12345!";
    private static final String ADMIN_ROLE = "ADMIN";
    private static final String DEMO_MANAGER_EMAIL = "demo@bcm-demo.local";

    private final UsersRepository usersRepository;
    private final RolesRepository rolesRepository;
    private final OrganizationRepository organizationRepository;
    private final ManagersRepository managersRepository;
    private final PasswordEncoder passwordEncoder;

    public DevDataSeeder(UsersRepository usersRepository,
                          RolesRepository rolesRepository,
                          OrganizationRepository organizationRepository,
                          ManagersRepository managersRepository,
                          PasswordEncoder passwordEncoder) {
        this.usersRepository = usersRepository;
        this.rolesRepository = rolesRepository;
        this.organizationRepository = organizationRepository;
        this.managersRepository = managersRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @SuppressWarnings("java:S6437") // same dev-only demo credential as DEMO_PASSWORD above, not a real secret
    public void run(ApplicationArguments args) {
        if (usersRepository.findByUsername(DEMO_USERNAME).isPresent()) {
            return;
        }

        Organization org = organizationRepository.findFirstByOrderByIdAsc().orElse(null);
        Roles adminRole = rolesRepository.findByRole(ADMIN_ROLE).orElse(null);
        if (org == null || adminRole == null) {
            // Reference data (V2/V7) isn't there yet for some reason — skip
            // rather than fail application startup over a convenience account.
            logger.warn("Skipping demo account seed: no organization or ADMIN role found");
            return;
        }

        Managers manager = managersRepository.save(Managers.builder()
                .firstName("Demo")
                .lastName("Admin")
                .email(DEMO_MANAGER_EMAIL)
                .phoneNumber("+10000000000")
                .department("Administration")
                .organization(org)
                .build());

        usersRepository.save(Users.builder()
                .username(DEMO_USERNAME)
                .passwordHash(passwordEncoder.encode(DEMO_PASSWORD))
                .role(adminRole)
                .organization(org)
                .manager(manager)
                .verified(true)
                .build());

        logger.info("=================================================================");
        logger.info("Demo account seeded (dev profile only) — username: {} / password: {}",
                DEMO_USERNAME, DEMO_PASSWORD);
        logger.info("=================================================================");
    }
}
