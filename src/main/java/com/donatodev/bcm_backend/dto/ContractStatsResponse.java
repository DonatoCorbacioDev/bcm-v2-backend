package com.donatodev.bcm_backend.dto;

public class ContractStatsResponse {
    private final int total;
    private final int active;
    private final int expiring;
    private final int expired;

    public ContractStatsResponse(int total, int active, int expiring, int expired) {
        this.total = total;
        this.active = active;
        this.expiring = expiring;
        this.expired = expired;
    }

    public int getTotal() {
        return total;
    }

    public int getActive() {
        return active;
    }

    public int getExpiring() {
        return expiring;
    }

    public int getExpired() {
        return expired;
    }
}