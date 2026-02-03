package com.donatodev.bcm_backend.dto;

public class TopManagerDTO {

    private final Long managerId;
    private final String managerName;
    private final Long contractsCount;

    public TopManagerDTO(Long managerId, String managerName, Long contractsCount) {
        this.managerId = managerId;
        this.managerName = managerName;
        this.contractsCount = contractsCount;
    }

    public Long getManagerId() {
        return managerId;
    }

    public String getManagerName() {
        return managerName;
    }

    public Long getContractsCount() {
        return contractsCount;
    }
}
