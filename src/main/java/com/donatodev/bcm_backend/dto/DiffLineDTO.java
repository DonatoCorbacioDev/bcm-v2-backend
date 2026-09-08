/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.dto;

/**
 * One row of a redlined document comparison. {@code tag} is one of
 * EQUAL, INSERT, DELETE, CHANGE (mirrors java-diff-utils' DiffRow.Tag).
 * For EQUAL, oldText and newText are identical; for INSERT, oldText is
 * null; for DELETE, newText is null; for CHANGE both are set and differ.
 */
public record DiffLineDTO(
        String tag,
        String oldText,
        String newText
) {}
