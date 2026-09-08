/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.dto;

public class ContractsTimelineDTO {

    private final String month;
    private final Long count;

    public ContractsTimelineDTO(String month, Long count) {
        this.month = month;
        this.count = count;
    }

    public String getMonth() {
        return month;
    }

    public Long getCount() {
        return count;
    }
}
