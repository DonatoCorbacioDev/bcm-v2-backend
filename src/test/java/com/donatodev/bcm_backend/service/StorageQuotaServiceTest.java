/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.service;

import static com.donatodev.bcm_backend.support.TestIds.uid;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

import com.donatodev.bcm_backend.entity.Organization;
import com.donatodev.bcm_backend.entity.SubscriptionTier;
import com.donatodev.bcm_backend.exception.TierLimitExceededException;
import com.donatodev.bcm_backend.repository.ContractDocumentRepository;
import com.donatodev.bcm_backend.repository.ElectronicInvoiceRepository;
import com.donatodev.bcm_backend.repository.OrganizationRepository;

@ExtendWith(MockitoExtension.class)
class StorageQuotaServiceTest {

    private static final long MB = StorageQuotaService.MB;

    @Mock private OrganizationRepository organizations;
    @Mock private ContractDocumentRepository documents;
    @Mock private ElectronicInvoiceRepository invoices;
    @InjectMocks private StorageQuotaService quota;

    private void organization(SubscriptionTier tier, long documentBytes, long invoiceBytes) {
        when(organizations.findByIdForUpdate(uid(1)))
                .thenReturn(Optional.of(Organization.builder().id(uid(1)).subscriptionTier(tier).build()));
        if (tier != SubscriptionTier.ENTERPRISE) {
            when(documents.sumFileSizeByOrganization(uid(1))).thenReturn(documentBytes);
            when(invoices.sumFileSizeByOrganization(uid(1))).thenReturn(invoiceBytes);
        }
    }

    @Test
    @DisplayName("a file that fits in the FREE quota, documents and invoices counted together, is let through")
    void fits() {
        organization(SubscriptionTier.FREE, 200 * MB, 40 * MB);
        assertThatCode(() -> quota.requireRoom(uid(1), 10 * MB)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a file that would take the organization past its quota is refused, saying how much is used")
    void refused() {
        organization(SubscriptionTier.FREE, 200 * MB, 45 * MB);
        assertThatThrownBy(() -> quota.requireRoom(uid(1), 10 * MB))
                .isInstanceOf(TierLimitExceededException.class)
                .hasMessageContaining("245 MB su 250 MB");
    }

    @Test
    @DisplayName("exactly reaching the quota is allowed; one byte more is not")
    void boundary() {
        organization(SubscriptionTier.FREE, 250 * MB - 100, 0);
        assertThatCode(() -> quota.requireRoom(uid(1), 100)).doesNotThrowAnyException();
        assertThatThrownBy(() -> quota.requireRoom(uid(1), 101)).isInstanceOf(TierLimitExceededException.class);
    }

    @Test
    @DisplayName("PRO has 5 GB")
    void pro() {
        organization(SubscriptionTier.PRO, 4 * 1024 * MB, 0);
        assertThatCode(() -> quota.requireRoom(uid(1), 10 * MB)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("ENTERPRISE has no quota, and nothing is summed for it")
    void enterprise() {
        organization(SubscriptionTier.ENTERPRISE, 0, 0);
        assertThatCode(() -> quota.requireRoom(uid(1), 10 * MB)).doesNotThrowAnyException();
        verify(documents, never()).sumFileSizeByOrganization(uid(1));
    }

    @Test
    @DisplayName("no organization in context: nothing to check")
    void noOrganization() {
        assertThatCode(() -> quota.requireRoom(null, 10 * MB)).doesNotThrowAnyException();
        verify(organizations, never()).findByIdForUpdate(uid(1));
    }
}
