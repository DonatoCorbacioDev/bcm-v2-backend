package com.donatodev.bcm_backend.service;

import static org.mockito.ArgumentMatchers.any;
import static com.donatodev.bcm_backend.support.TestIds.uid;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
        verify(organizationRepository, never()).findById(any(UUID.class));
    }

    @Test
    @DisplayName("No-op when the organization can't be resolved")
    void shouldDoNothingWhenOrganizationNotFound() {
        when(organizationRepository.findById(uid(99))).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> tierLimitService.enforceContractCreationLimit(uid(99)));
        verify(contractsRepository, never()).countAllContractsByOrg(any(UUID.class));
    }

    @Test
    @DisplayName("FREE tier under the limit: allowed")
    void shouldAllowCreationUnderFreeLimit() {
        Organization org = Organization.builder().id(uid(1)).subscriptionTier(SubscriptionTier.FREE).build();
        when(organizationRepository.findById(uid(1))).thenReturn(Optional.of(org));
        when(contractsRepository.countAllContractsByOrg(uid(1))).thenReturn(14);

        assertDoesNotThrow(() -> tierLimitService.enforceContractCreationLimit(uid(1)));
    }

    @Test
    @DisplayName("FREE tier at the limit: rejected")
    void shouldRejectCreationAtFreeLimit() {
        Organization org = Organization.builder().id(uid(1)).subscriptionTier(SubscriptionTier.FREE).build();
        when(organizationRepository.findById(uid(1))).thenReturn(Optional.of(org));
        when(contractsRepository.countAllContractsByOrg(uid(1))).thenReturn(15);

        UUID id1 = uid(1);
        TierLimitExceededException ex = assertThrows(TierLimitExceededException.class,
                () -> tierLimitService.enforceContractCreationLimit(id1));
        assertTrue(ex.getMessage().contains("FREE"));
    }

    @Test
    @DisplayName("PRO tier under the limit: allowed")
    void shouldAllowCreationUnderProLimit() {
        Organization org = Organization.builder().id(uid(2)).subscriptionTier(SubscriptionTier.PRO).build();
        when(organizationRepository.findById(uid(2))).thenReturn(Optional.of(org));
        when(contractsRepository.countAllContractsByOrg(uid(2))).thenReturn(199);

        assertDoesNotThrow(() -> tierLimitService.enforceContractCreationLimit(uid(2)));
    }

    @Test
    @DisplayName("PRO tier at the limit: rejected")
    void shouldRejectCreationAtProLimit() {
        Organization org = Organization.builder().id(uid(2)).subscriptionTier(SubscriptionTier.PRO).build();
        when(organizationRepository.findById(uid(2))).thenReturn(Optional.of(org));
        when(contractsRepository.countAllContractsByOrg(uid(2))).thenReturn(200);

        UUID id2 = uid(2);
        assertThrows(TierLimitExceededException.class,
                () -> tierLimitService.enforceContractCreationLimit(id2));
    }

    @Test
    @DisplayName("ENTERPRISE tier is unlimited: never counts contracts")
    void shouldNeverLimitEnterpriseTier() {
        Organization org = Organization.builder().id(uid(3)).subscriptionTier(SubscriptionTier.ENTERPRISE).build();
        when(organizationRepository.findById(uid(3))).thenReturn(Optional.of(org));

        assertDoesNotThrow(() -> tierLimitService.enforceContractCreationLimit(uid(3)));
        verify(contractsRepository, never()).countAllContractsByOrg(any(UUID.class));
    }

    @Test
    @DisplayName("requireMlAccess: no-op when orgId is null")
    void requireMlAccessNoOpWhenOrgIdNull() {
        assertDoesNotThrow(() -> tierLimitService.requireMlAccess(null));
        verify(organizationRepository, never()).findById(any(UUID.class));
    }

    @Test
    @DisplayName("requireMlAccess: no-op when the organization can't be resolved")
    void requireMlAccessNoOpWhenOrganizationNotFound() {
        when(organizationRepository.findById(uid(99))).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> tierLimitService.requireMlAccess(uid(99)));
    }

    @Test
    @DisplayName("requireMlAccess: FREE tier is denied")
    void requireMlAccessDeniesFreeTier() {
        Organization org = Organization.builder().id(uid(1)).subscriptionTier(SubscriptionTier.FREE).build();
        when(organizationRepository.findById(uid(1))).thenReturn(Optional.of(org));

        UUID id1 = uid(1);
        assertThrows(AccessDeniedException.class, () -> tierLimitService.requireMlAccess(id1));
    }

    @Test
    @DisplayName("requireMlAccess: PRO tier is allowed")
    void requireMlAccessAllowsProTier() {
        Organization org = Organization.builder().id(uid(2)).subscriptionTier(SubscriptionTier.PRO).build();
        when(organizationRepository.findById(uid(2))).thenReturn(Optional.of(org));

        assertDoesNotThrow(() -> tierLimitService.requireMlAccess(uid(2)));
    }

    @Test
    @DisplayName("requireMlAccess: ENTERPRISE tier is allowed")
    void requireMlAccessAllowsEnterpriseTier() {
        Organization org = Organization.builder().id(uid(3)).subscriptionTier(SubscriptionTier.ENTERPRISE).build();
        when(organizationRepository.findById(uid(3))).thenReturn(Optional.of(org));

        assertDoesNotThrow(() -> tierLimitService.requireMlAccess(uid(3)));
    }

    @Test
    @DisplayName("getContractLimit: FREE is 15")
    void getContractLimitFree() {
        assertEquals(15, tierLimitService.getContractLimit(SubscriptionTier.FREE));
    }

    @Test
    @DisplayName("getContractLimit: PRO is 200")
    void getContractLimitPro() {
        assertEquals(200, tierLimitService.getContractLimit(SubscriptionTier.PRO));
    }

    @Test
    @DisplayName("getContractLimit: ENTERPRISE is null (unlimited)")
    void getContractLimitEnterpriseIsNull() {
        assertNull(tierLimitService.getContractLimit(SubscriptionTier.ENTERPRISE));
    }
}
