package com.donatodev.bcm_backend.mapper;

import static com.donatodev.bcm_backend.support.TestIds.uid;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.donatodev.bcm_backend.dto.FinancialTypeDTO;
import com.donatodev.bcm_backend.entity.FinancialCategory;
import com.donatodev.bcm_backend.entity.FinancialTypes;

@SpringBootTest
@ActiveProfiles("test")
class FinancialTypeMapperTest {

    @Autowired
    private FinancialTypeMapper financialTypeMapper;

    @Nested
    class ToDTOTests {

        @Test
        void shouldMapEntityToDTO() {
            FinancialTypes type = FinancialTypes.builder()
                    .id(uid(1))
                    .name("SALES")
                    .description("Revenue-related transactions")
                    .category(FinancialCategory.REVENUE)
                    .build();

            FinancialTypeDTO dto = financialTypeMapper.toDTO(type);

            assertEquals(uid(1), dto.id());
            assertEquals("SALES", dto.name());
            assertEquals("Revenue-related transactions", dto.description());
            assertEquals(FinancialCategory.REVENUE, dto.category());
        }

        @Test
        void shouldMapEntityToDTOWithNullDescription() {
            FinancialTypes type = FinancialTypes.builder()
                    .id(uid(2))
                    .name("COSTS")
                    .description(null)
                    .category(FinancialCategory.COST)
                    .build();

            FinancialTypeDTO dto = financialTypeMapper.toDTO(type);

            assertEquals(uid(2), dto.id());
            assertEquals("COSTS", dto.name());
            assertNull(dto.description());
            assertEquals(FinancialCategory.COST, dto.category());
        }

        @Test
        void shouldMapEntityToDTOWithNullId() {
            FinancialTypes type = FinancialTypes.builder()
                    .id(null)
                    .name("INVESTMENTS")
                    .description("Capital investments")
                    .category(FinancialCategory.COST)
                    .build();

            FinancialTypeDTO dto = financialTypeMapper.toDTO(type);

            assertNull(dto.id());
            assertEquals("INVESTMENTS", dto.name());
            assertEquals("Capital investments", dto.description());
        }
    }

    @Nested
    class ToEntityTests {

        @Test
        void shouldMapDTOToEntity() {
            FinancialTypeDTO dto = new FinancialTypeDTO(uid(10), "SALES", "Revenue-related transactions", FinancialCategory.REVENUE);

            FinancialTypes type = financialTypeMapper.toEntity(dto);

            assertEquals(uid(10), type.getId());
            assertEquals("SALES", type.getName());
            assertEquals("Revenue-related transactions", type.getDescription());
            assertEquals(FinancialCategory.REVENUE, type.getCategory());
        }

        @Test
        void shouldMapDTOToEntityWithNullDescription() {
            FinancialTypeDTO dto = new FinancialTypeDTO(uid(11), "COSTS", null, FinancialCategory.COST);

            FinancialTypes type = financialTypeMapper.toEntity(dto);

            assertEquals(uid(11), type.getId());
            assertEquals("COSTS", type.getName());
            assertNull(type.getDescription());
            assertEquals(FinancialCategory.COST, type.getCategory());
        }

        @Test
        void shouldMapDTOToEntityWithNullId() {
            FinancialTypeDTO dto = new FinancialTypeDTO(null, "INVESTMENTS", "Capital investments", FinancialCategory.COST);

            FinancialTypes type = financialTypeMapper.toEntity(dto);

            assertNull(type.getId());
            assertEquals("INVESTMENTS", type.getName());
            assertEquals("Capital investments", type.getDescription());
        }
    }
}
