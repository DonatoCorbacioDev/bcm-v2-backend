package com.donatodev.bcm_backend.service;

import org.springframework.stereotype.Component;

import com.donatodev.bcm_backend.config.TenantContext;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.exception.UserNotFoundException;
import com.donatodev.bcm_backend.repository.UsersRepository;
import com.donatodev.bcm_backend.util.AuthenticatedUserUtils;

/**
 * Resolves the {@link Users} entity for the currently authenticated
 * principal, scoping the lookup to the current tenant when one is set.
 */
@Component
public class CurrentUserResolver {

    private final UsersRepository usersRepository;

    public CurrentUserResolver(UsersRepository usersRepository) {
        this.usersRepository = usersRepository;
    }

    public Users resolve() {
        String username = AuthenticatedUserUtils.getUsernameOrThrow();
        Long orgId = TenantContext.get();
        if (orgId != null) {
            return usersRepository.findByUsernameAndOrganizationId(username, orgId)
                    .orElseThrow(() -> new UserNotFoundException("User not found"));
        }
        return usersRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
    }
}
