/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.mapper;

import org.springframework.stereotype.Component;

import com.donatodev.bcm_backend.dto.ContractHistoryDTO;
import com.donatodev.bcm_backend.entity.ContractHistory;
import com.donatodev.bcm_backend.entity.Managers;
import com.donatodev.bcm_backend.entity.Users;

/**
 * Mapper class responsible for converting between {@link ContractHistory} entities
 * and {@link ContractHistoryDTO} data transfer objects.
 * <p>
 * One way only (OWASP API3): the history is written by the server on every
 * status change and never built from a request, so an entry can't be signed
 * with another user's name or given a made-up date.
 */
@Component
public class ContractHistoryMapper {

    /**
     * Converts a {@link ContractHistory} entity to a {@link ContractHistoryDTO}.
     *
     * @param history the contract history entity
     * @return the corresponding DTO
     */
    public ContractHistoryDTO toDTO(ContractHistory history) {
        Users author = history.getModifiedBy();
        return new ContractHistoryDTO(
                history.getId(),
                history.getContract().getId(),
                author != null ? author.getId() : null,
                history.getModificationDate(),
                history.getPreviousStatus(),
                history.getNewStatus(),
                displayName(author)
        );
    }

    /** The person's name when the user has a manager profile, else the username. */
    private static String displayName(Users author) {
        if (author == null) {
            return null;
        }
        Managers manager = author.getManager();
        if (manager != null) {
            String name = ((manager.getFirstName() == null ? "" : manager.getFirstName()) + " "
                    + (manager.getLastName() == null ? "" : manager.getLastName())).trim();
            if (!name.isEmpty()) {
                return name;
            }
        }
        return author.getUsername();
    }
}
