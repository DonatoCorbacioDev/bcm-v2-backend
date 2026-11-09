package com.donatodev.bcm_backend.mapper;

import static com.donatodev.bcm_backend.support.TestIds.uid;
import java.time.LocalDateTime;
import java.time.Month;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

import com.donatodev.bcm_backend.dto.RiskFeedbackDTO;
import com.donatodev.bcm_backend.entity.Contracts;
import com.donatodev.bcm_backend.entity.RiskFeedback;

class RiskFeedbackMapperTest {

    private final RiskFeedbackMapper mapper = new RiskFeedbackMapper();

    @Test
    void shouldConvertToDTO() {
        RiskFeedback feedback = RiskFeedback.builder()
                .id(uid(1))
                .contract(Contracts.builder().id(uid(42)).build())
                .riskScore(0.72)
                .riskLevel("HIGH")
                .mlScore(0.65)
                .mlLevel("HIGH")
                .agree(true)
                .createdAt(LocalDateTime.of(2027, Month.JUNE, 15, 9, 30))
                .build();

        RiskFeedbackDTO dto = mapper.toDTO(feedback);

        assertEquals(uid(1), dto.id());
        assertEquals(uid(42), dto.contractId());
        assertEquals(0.72, dto.riskScore());
        assertEquals("HIGH", dto.riskLevel());
        assertEquals(0.65, dto.mlScore());
        assertEquals("HIGH", dto.mlLevel());
        assertEquals(true, dto.agree());
        assertEquals(LocalDateTime.of(2027, Month.JUNE, 15, 9, 30), dto.createdAt());
    }

    @Test
    void shouldConvertToDTOWithoutMlFields() {
        RiskFeedback feedback = RiskFeedback.builder()
                .id(uid(2))
                .contract(Contracts.builder().id(uid(7)).build())
                .riskScore(0.2)
                .riskLevel("LOW")
                .agree(false)
                .createdAt(LocalDateTime.of(2027, Month.JUNE, 15, 9, 30))
                .build();

        RiskFeedbackDTO dto = mapper.toDTO(feedback);

        assertEquals(uid(2), dto.id());
        assertEquals(uid(7), dto.contractId());
        assertEquals(null, dto.mlScore());
        assertEquals(null, dto.mlLevel());
        assertEquals(false, dto.agree());
    }
}
