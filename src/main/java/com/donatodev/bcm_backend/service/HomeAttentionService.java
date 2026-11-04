/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.donatodev.bcm_backend.config.TenantContext;
import com.donatodev.bcm_backend.dto.HomeAttentionDTO;
import com.donatodev.bcm_backend.dto.HomeAttentionDTO.Item;
import com.donatodev.bcm_backend.dto.HomeAttentionDTO.Type;
import com.donatodev.bcm_backend.entity.ContractStatus;
import com.donatodev.bcm_backend.entity.Contracts;
import com.donatodev.bcm_backend.entity.Counterparty;
import com.donatodev.bcm_backend.entity.ElectronicInvoice;
import com.donatodev.bcm_backend.entity.InvoiceMatchStatus;
import com.donatodev.bcm_backend.entity.Managers;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.repository.ContractsRepository;
import com.donatodev.bcm_backend.repository.ElectronicInvoiceRepository;

/**
 * Builds the home page's "what to do" list for the current user: an ADMIN
 * sees the whole organization, a MANAGER only the contracts assigned to them.
 * Fails closed: without an organization in the request there is nothing.
 */
@Service
public class HomeAttentionService {

    /** How far ahead contract dates are looked at; same window as the "in scadenza" badge. */
    static final int WINDOW_DAYS = 30;

    private static final Set<InvoiceMatchStatus> TO_MATCH = EnumSet.of(
            InvoiceMatchStatus.UNMATCHED, InvoiceMatchStatus.SUGGESTED, InvoiceMatchStatus.COUNTERPARTY_MISMATCH);

    private final ContractsRepository contractsRepository;
    private final ElectronicInvoiceRepository invoiceRepository;
    private final CurrentUserResolver currentUserResolver;

    public HomeAttentionService(ContractsRepository contractsRepository,
                                ElectronicInvoiceRepository invoiceRepository,
                                CurrentUserResolver currentUserResolver) {
        this.contractsRepository = contractsRepository;
        this.invoiceRepository = invoiceRepository;
        this.currentUserResolver = currentUserResolver;
    }

    @Transactional(readOnly = true)
    public HomeAttentionDTO getAttention() {
        Long orgId = TenantContext.get();
        if (orgId == null) {
            return new HomeAttentionDTO(0, 0, 0, WINDOW_DAYS, List.of());
        }
        Users user = currentUserResolver.resolve();
        boolean admin = "ADMIN".equals(user.getRole().getRole());
        Long managerId = user.getManager() != null ? user.getManager().getId() : null;
        if (!admin && managerId == null) {
            return new HomeAttentionDTO(0, 0, 0, WINDOW_DAYS, List.of());
        }

        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        List<Contracts> contracts = admin
                ? contractsRepository.findByStatusAndOrganization_Id(ContractStatus.ACTIVE, orgId)
                : contractsRepository.findByManagerIdAndStatus(managerId, ContractStatus.ACTIVE).stream()
                        .filter(c -> c.getOrganization() != null && orgId.equals(c.getOrganization().getId()))
                        .toList();

        List<Item> contractItems = new ArrayList<>();
        for (Contracts contract : contracts) {
            Item item = contractItem(contract, today);
            if (item != null) {
                contractItems.add(item);
            }
        }

        List<Item> invoicesToMatch = new ArrayList<>();
        List<Item> ibanAlerts = new ArrayList<>();
        collectInvoiceItems(orgId, admin, managerId, today, invoicesToMatch, ibanAlerts);

        // A possible fraud first, then dates in order, then the invoices to file.
        Comparator<Item> byDate = Comparator.comparing(Item::dueDate, Comparator.nullsLast(Comparator.naturalOrder()));
        ibanAlerts.sort(byDate);
        contractItems.sort(byDate);
        invoicesToMatch.sort(byDate);
        List<Item> items = new ArrayList<>(ibanAlerts);
        items.addAll(contractItems);
        items.addAll(invoicesToMatch);

        return new HomeAttentionDTO(contractItems.size(), invoicesToMatch.size(), ibanAlerts.size(),
                WINDOW_DAYS, List.copyOf(items));
    }

    private void collectInvoiceItems(Long orgId, boolean admin, Long managerId, LocalDate today,
                                     List<Item> invoicesToMatch, List<Item> ibanAlerts) {
        for (ElectronicInvoice invoice : invoiceRepository.findByOrgIdAndSepaBatchIsNull(orgId)) {
            Contracts contract = invoice.getContract();
            if (contract == null || (!admin && !isManagedBy(contract, managerId))) {
                continue;
            }
            if (ibanChanged(invoice, contract.getCounterparty())) {
                ibanAlerts.add(invoiceItem(Type.IBAN_CHANGED, invoice, contract, today));
            }
            if (TO_MATCH.contains(invoice.getMatchStatus())) {
                invoicesToMatch.add(invoiceItem(Type.INVOICE_TO_MATCH, invoice, contract, today));
            }
        }

    }

    /** The contract's item, if any of its dates needs action within the window. */
    private Item contractItem(Contracts contract, LocalDate today) {
        LocalDate horizon = today.plusDays(WINDOW_DAYS);
        LocalDate endDate = contract.getEndDate();
        if (endDate == null) {
            return null;
        }
        if (Boolean.TRUE.equals(contract.getAutoRenew())) {
            LocalDate deadline = contract.noticeDeadline();
            if (deadline != null && contract.getRenewalMonths() != null) {
                // Deadline passed: it will renew, nothing to do any more.
                return !deadline.isBefore(today) && !deadline.isAfter(horizon)
                        ? contractItem(Type.NOTICE_DEADLINE, contract, deadline, today)
                        : null;
            }
            // Renews tacitly but BCM can't tell when to act or what happens: ask.
            return endDate.isAfter(horizon) ? null : contractItem(Type.RENEWAL_TO_CHECK, contract, endDate, today);
        }
        return !endDate.isBefore(today) && !endDate.isAfter(horizon)
                ? contractItem(Type.EXPIRY, contract, endDate, today)
                : null;
    }

    private static Item contractItem(Type type, Contracts contract, LocalDate date, LocalDate today) {
        return new Item(type, contract.getId(), contract.getContractNumber(), counterpartyName(contract),
                contract.getProjectName(), date, (int) ChronoUnit.DAYS.between(today, date), managerName(contract),
                type == Type.NOTICE_DEADLINE ? contract.getRenewalMonths() : null, null, null, null);
    }

    private static Item invoiceItem(Type type, ElectronicInvoice invoice, Contracts contract, LocalDate today) {
        LocalDate date = invoice.getInvoiceDate();
        return new Item(type, contract.getId(), contract.getContractNumber(),
                invoice.getSupplierName() != null ? invoice.getSupplierName() : counterpartyName(contract),
                contract.getProjectName(), date, date != null ? (int) ChronoUnit.DAYS.between(today, date) : null,
                managerName(contract), null, invoice.getId(), invoice.getInvoiceNumber(), invoice.comparableAmount());
    }

    /** Same comparison the SEPA payment makes before refusing the transfer, so the two agree. */
    static boolean ibanChanged(ElectronicInvoice invoice, Counterparty counterparty) {
        if (invoice.getSupplierIban() == null || counterparty == null || counterparty.getVerifiedIban() == null) {
            return false;
        }
        String invoiceIban = invoice.getSupplierIban().replace(" ", "").toUpperCase(Locale.ROOT);
        return !counterparty.getVerifiedIban().equalsIgnoreCase(invoiceIban);
    }

    private static boolean isManagedBy(Contracts contract, Long managerId) {
        return contract.getManager() != null && Objects.equals(contract.getManager().getId(), managerId);
    }

    private static String counterpartyName(Contracts contract) {
        return contract.getCounterparty() != null ? contract.getCounterparty().getName() : null;
    }

    private static String managerName(Contracts contract) {
        Managers m = contract.getManager();
        return m != null ? m.getFirstName() + " " + m.getLastName() : null;
    }
}
