package com.donatodev.bcm_backend.mapper;

import org.springframework.stereotype.Component;

import com.donatodev.bcm_backend.dto.RiskFeedbackDTO;
import com.donatodev.bcm_backend.entity.RiskFeedback;

/**
 * Converts {@link RiskFeedback} entities to {@link RiskFeedbackDTO}.
 */
@Component
public class RiskFeedbackMapper {

    public RiskFeedbackDTO toDTO(RiskFeedback feedback) {
        return new RiskFeedbackDTO(
                feedback.getId(),
                feedback.getContract().getId(),
                feedback.getRiskScore(),
                feedback.getRiskLevel(),
                feedback.getMlScore(),
                feedback.getMlLevel(),
                feedback.isAgree(),
                feedback.getCreatedAt()
        );
    }
}
