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
