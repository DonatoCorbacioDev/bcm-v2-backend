package com.donatodev.bcm_backend.mapper;

import static com.donatodev.bcm_backend.support.TestIds.uid;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.donatodev.bcm_backend.dto.BusinessAreaDTO;
import com.donatodev.bcm_backend.entity.BusinessAreas;

@SpringBootTest
@ActiveProfiles("test")
class BusinessAreaMapperTest {

    @Autowired
    private BusinessAreaMapper businessAreaMapper;

    @Nested
    class ToDTOTests {

        @Test
        void shouldMapEntityToDTO() {
            BusinessAreas area = BusinessAreas.builder()
                    .id(uid(1))
                    .name("Finance")
                    .description("Handles budgets")
                    .build();

            BusinessAreaDTO dto = businessAreaMapper.toDTO(area);

            assertEquals(uid(1), dto.id());
            assertEquals("Finance", dto.name());
            assertEquals("Handles budgets", dto.description());
        }

        @Test
        void shouldMapEntityToDTOWithNullDescription() {
            BusinessAreas area = BusinessAreas.builder()
                    .id(uid(2))
                    .name("IT")
                    .description(null)
                    .build();

            BusinessAreaDTO dto = businessAreaMapper.toDTO(area);

            assertEquals(uid(2), dto.id());
            assertEquals("IT", dto.name());
            assertNull(dto.description());
        }

        @Test
        void shouldMapEntityToDTOWithNullId() {
            BusinessAreas area = BusinessAreas.builder()
                    .id(null)
                    .name("Logistics")
                    .description("Handles shipments")
                    .build();

            BusinessAreaDTO dto = businessAreaMapper.toDTO(area);

            assertNull(dto.id());
            assertEquals("Logistics", dto.name());
            assertEquals("Handles shipments", dto.description());
        }
    }

    @Nested
    class ToEntityTests {

        @Test
        void shouldMapDTOToEntity() {
            BusinessAreaDTO dto = new BusinessAreaDTO(uid(10), "Marketing", "Handles promotions");

            BusinessAreas area = businessAreaMapper.toEntity(dto);

            assertEquals(uid(10), area.getId());
            assertEquals("Marketing", area.getName());
            assertEquals("Handles promotions", area.getDescription());
        }

        @Test
        void shouldMapDTOToEntityWithNullDescription() {
            BusinessAreaDTO dto = new BusinessAreaDTO(uid(11), "HR", null);

            BusinessAreas area = businessAreaMapper.toEntity(dto);

            assertEquals(uid(11), area.getId());
            assertEquals("HR", area.getName());
            assertNull(area.getDescription());
        }

        @Test
        void shouldMapDTOToEntityWithNullId() {
            BusinessAreaDTO dto = new BusinessAreaDTO(null, "Sales", "Revenue generation");

            BusinessAreas area = businessAreaMapper.toEntity(dto);

            assertNull(area.getId());
            assertEquals("Sales", area.getName());
            assertEquals("Revenue generation", area.getDescription());
        }
    }
}
