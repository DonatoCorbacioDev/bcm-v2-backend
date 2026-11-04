/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.integration;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;

import com.donatodev.bcm_backend.entity.Managers;
import com.donatodev.bcm_backend.entity.Organization;
import com.donatodev.bcm_backend.entity.Roles;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.repository.ManagersRepository;
import com.donatodev.bcm_backend.repository.OrganizationRepository;
import com.donatodev.bcm_backend.repository.RolesRepository;
import com.donatodev.bcm_backend.repository.UsersRepository;
import com.donatodev.bcm_backend.support.AbstractMySQLIntegrationTest;

/**
 * V51 on real MySQL: a username is unique across all organizations, so a
 * second organization can't take a username another one already uses -
 * which is what every username-only lookup relies on.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("Integration Test: usernames are unique across organizations (V51)")
class UsernameUniquenessIT extends AbstractMySQLIntegrationTest {

    @Autowired private UsersRepository usersRepository;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private RolesRepository rolesRepository;
    @Autowired private ManagersRepository managersRepository;

    @Test
    @DisplayName("The same username in a second organization is rejected by the database")
    void sameUsernameInAnotherOrganizationIsRejected() {
        Roles admin = rolesRepository.findByRole("ADMIN").orElseThrow();
        Organization first = organizationRepository.save(Organization.builder()
                .name("Username IT Org A").slug("username-it-org-a").build());
        Organization second = organizationRepository.save(Organization.builder()
                .name("Username IT Org B").slug("username-it-org-b").build());

        usersRepository.saveAndFlush(user("shared-name", admin, first, "a"));
        Users clash = user("shared-name", admin, second, "b");

        DataIntegrityViolationException e = assertThrows(DataIntegrityViolationException.class,
                () -> usersRepository.saveAndFlush(clash));
        // The username constraint itself, not some other column failing.
        assertTrue(e.getMessage().contains("uq_usr_username"), e.getMessage());
    }

    private Users user(String username, Roles role, Organization org, String suffix) {
        Managers manager = managersRepository.save(Managers.builder()
                .firstName("Username").lastName("IT " + suffix)
                .email("username-it-" + suffix + "@example.com")
                .organization(org)
                .build());
        return Users.builder()
                .username(username)
                .passwordHash("not-a-real-hash")
                .role(role)
                .manager(manager)
                .organization(org)
                .build();
    }
}
