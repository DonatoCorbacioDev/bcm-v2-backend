package com.donatodev.bcm_backend.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.security.access.AccessDeniedException;

import com.donatodev.bcm_backend.entity.Organization;
import com.donatodev.bcm_backend.entity.SubscriptionTier;
import com.donatodev.bcm_backend.exception.TierLimitExceededException;
import com.donatodev.bcm_backend.repository.ContractsRepository;
import com.donatodev.bcm_backend.repository.OrganizationRepository;

/**
 * Unit tests for {@link TierLimitService}.
 */
@ExtendWith(MockitoExtension.class)
class TierLimitServiceTest {

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private ContractsRepository contractsRepository;

    @InjectMocks
    private TierLimitService tierLimitService;

    @Test
    @DisplayName("No-op when there is no tenant context (orgId null)")
    void shouldDoNothingWhenOrgIdIsNull() {
        assertDoesNotThrow(() -> tierLimitService.enforceContractCreationLimit(null));
        verify(organizationRepository, never()).findById(anyLong());
    }

    @Test
    @DisplayName("No-op when the organization can't be resolved")
    void shouldDoNothingWhenOrganizationNotFound() {
        when(organizationRepository.findById(99L)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> tierLimitService.enforceContractCreationLimit(99L));
        verify(contractsRepository, never()).countAllContractsByOrg(anyLong());
    }

    @Test
    @DisplayName("FREE tier under the limit: allowed")
    void shouldAllowCreationUnderFreeLimit() {
        Organization org = Organization.builder().id(1L).subscriptionTier(SubscriptionTier.FREE).build();
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(org));
        when(contractsRepository.countAllContractsByOrg(1L)).thenReturn(14);

        assertDoesNotThrow(() -> tierLimitService.enforceContractCreationLimit(1L));
    }

    @Test
    @DisplayName("FREE tier at the limit: rejected")
    void shouldRejectCreationAtFreeLimit() {
        Organization org = Organization.builder().id(1L).subscriptionTier(SubscriptionTier.FREE).build();
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(org));
        when(contractsRepository.countAllContractsByOrg(1L)).thenReturn(15);

        TierLimitExceededException ex = assertThrows(TierLimitExceededException.class,
                () -> tierLimitService.enforceContractCreationLimit(1L));
        assertTrue(ex.getMessage().contains("FREE"));
    }

    @Test
    @DisplayName("PRO tier under the limit: allowed")
    void shouldAllowCreationUnderProLimit() {
        Organization org = Organization.builder().id(2L).subscriptionTier(SubscriptionTier.PRO).build();
        when(organizationRepository.findById(2L)).thenReturn(Optional.of(org));
        when(contractsRepository.countAllContractsByOrg(2L)).thenReturn(199);

        assertDoesNotThrow(() -> tierLimitService.enforceContractCreationLimit(2L));
    }

    @Test
    @DisplayName("PRO tier at the limit: rejected")
    void shouldRejectCreationAtProLimit() {
        Organization org = Organization.builder().id(2L).subscriptionTier(SubscriptionTier.PRO).build();
        when(organizationRepository.findById(2L)).thenReturn(Optional.of(org));
        when(contractsRepository.countAllContractsByOrg(2L)).thenReturn(200);

        assertThrows(TierLimitExceededException.class,
                () -> tierLimitService.enforceContractCreationLimit(2L));
    }

    @Test
    @DisplayName("ENTERPRISE tier is unlimited: never counts contracts")
    void shouldNeverLimitEnterpriseTier() {
        Organization org = Organization.builder().id(3L).subscriptionTier(SubscriptionTier.ENTERPRISE).build();
        when(organizationRepository.findById(3L)).thenReturn(Optional.of(org));

        assertDoesNotThrow(() -> tierLimitService.enforceContractCreationLimit(3L));
        verify(contractsRepository, never()).countAllContractsByOrg(anyLong());
    }

    @Test
    @DisplayName("requireMlAccess: no-op when orgId is null")
    void requireMlAccessNoOpWhenOrgIdNull() {
        assertDoesNotThrow(() -> tierLimitService.requireMlAccess(null));
        verify(organizationRepository, never()).findById(anyLong());
    }

    @Test
    @DisplayName("requireMlAccess: no-op when the organization can't be resolved")
    void requireMlAccessNoOpWhenOrganizationNotFound() {
        when(organizationRepository.findById(99L)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> tierLimitService.requireMlAccess(99L));
    }

    @Test
    @DisplayName("requireMlAccess: FREE tier is denied")
    void requireMlAccessDeniesFreeTier() {
        Organization org = Organization.builder().id(1L).subscriptionTier(SubscriptionTier.FREE).build();
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(org));

        assertThrows(AccessDeniedException.class, () -> tierLimitService.requireMlAccess(1L));
    }

    @Test
    @DisplayName("requireMlAccess: PRO tier is allowed")
    void requireMlAccessAllowsProTier() {
        Organization org = Organization.builder().id(2L).subscriptionTier(SubscriptionTier.PRO).build();
        when(organizationRepository.findById(2L)).thenReturn(Optional.of(org));

        assertDoesNotThrow(() -> tierLimitService.requireMlAccess(2L));
    }

    @Test
    @DisplayName("requireMlAccess: ENTERPRISE tier is allowed")
    void requireMlAccessAllowsEnterpriseTier() {
        Organization org = Organization.builder().id(3L).subscriptionTier(SubscriptionTier.ENTERPRISE).build();
        when(organizationRepository.findById(3L)).thenReturn(Optional.of(org));

        assertDoesNotThrow(() -> tierLimitService.requireMlAccess(3L));
    }
}
